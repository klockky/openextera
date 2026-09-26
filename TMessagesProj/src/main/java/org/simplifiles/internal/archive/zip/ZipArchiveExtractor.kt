package org.simplifiles.internal.archive.zip

import org.simplifiles.archive.ArchiveEntryInfo
import org.simplifiles.archive.ArchiveExtractionOptions
import org.simplifiles.archive.ArchiveFormat
import org.simplifiles.archive.ArchiveIssue
import org.simplifiles.archive.ArchiveIssueSeverity
import org.simplifiles.archive.ExtractedArchive
import org.simplifiles.archive.ExtractionTargetPolicy
import org.simplifiles.archive.ValidationReport
import org.simplifiles.archive.security.DuplicatePolicy
import org.simplifiles.archive.security.SecurityPolicy
import org.simplifiles.exception.ArchiveOperationCanceledException
import org.simplifiles.exception.ArchiveValidationException
import org.simplifiles.exception.ExtractionTargetException
import org.simplifiles.internal.archive.ArchivePathAnalyzer
import org.simplifiles.internal.archive.ArchivePathResolver
import org.simplifiles.internal.io.FileTreeCleaner
import org.simplifiles.internal.saturatingPlus
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal object ZipArchiveExtractor {

    fun extract(
        archive: Path,
        target: Path,
        policy: SecurityPolicy,
        cleanupOnClose: Boolean,
        entries: List<ArchiveEntryInfo>,
        options: ArchiveExtractionOptions
    ): ExtractedArchive {
        val root = target.toAbsolutePath().normalize()
        val targetExisted = Files.exists(root)
        val progress = ExtractionProgress(options, entries.size.toLong(), knownUncompressedSize(entries))
        progress.checkCanceled()
        prepareTarget(root, options.targetPolicy)
        try {
            progress.emit(null)
            extractEntries(archive, root, policy, progress, options.bufferSize)
            return ExtractedArchive(root, cleanupOnClose)
        } catch (t: Throwable) {
            if (targetExisted) {
                FileTreeCleaner.deleteContents(root)
            } else {
                FileTreeCleaner.deleteRecursively(root)
            }
            throw t
        }
    }

    private fun prepareTarget(root: Path, targetPolicy: ExtractionTargetPolicy) {
        if (Files.exists(root)) {
            when (targetPolicy) {
                ExtractionTargetPolicy.ERROR_IF_NOT_EMPTY -> {
                    if (!Files.isDirectory(root)) {
                        throw ExtractionTargetException(root, "target exists but is not a directory")
                    }
                    if (!isDirectoryEmpty(root)) {
                        throw ExtractionTargetException(root, "target directory must be empty")
                    }
                    return
                }
                ExtractionTargetPolicy.CLEAN -> {
                    if (!Files.isDirectory(root)) {
                        throw ExtractionTargetException(root, "target exists but is not a directory")
                    }
                    FileTreeCleaner.deleteContents(root)
                    return
                }
                ExtractionTargetPolicy.REPLACE -> FileTreeCleaner.deleteRecursively(root)
            }
        }
        Files.createDirectories(root)
    }

    private fun isDirectoryEmpty(directory: Path): Boolean {
        return Files.list(directory).use { !it.findAny().isPresent }
    }

    private fun extractEntries(archive: Path, root: Path, policy: SecurityPolicy, progress: ExtractionProgress, bufferSize: Int) {
        ZipFile(archive.toFile()).use { zip ->
            val seenPaths = LinkedHashSet<String>()
            var totalWritten = 0L
            for (entry in zip.entries().asSequence()) {
                progress.checkCanceled()
                val destinationPath = destinationPathFor(entry, seenPaths, policy)
                if (destinationPath == null) {
                    progress.entryCompleted(entry.name)
                    continue
                }
                // every entry is re-resolved against the root at write time, independently of the pre-validation
                val destination = ArchivePathResolver.resolve(root, destinationPath, policy.allowAbsolutePaths)
                if (entry.isDirectory) {
                    Files.createDirectories(destination)
                    progress.entryCompleted(destinationPath)
                    continue
                }
                Files.createDirectories(destination.parent)
                if (policy.duplicatePolicy == DuplicatePolicy.KEEP_LAST) {
                    Files.deleteIfExists(destination)
                }
                val written = zip.getInputStream(entry).use { input ->
                    Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                        copyWithLimits(input, output, entry, totalWritten, policy, progress, destinationPath, bufferSize)
                    }
                }
                totalWritten = checkedAddTotal(totalWritten, written, policy, entry.name)
                validateRuntimeCompressionRatio(entry, written, policy)
                progress.entryCompleted(destinationPath)
            }
        }
    }

    /** Returns the normalized destination path of [entry], or null if the entry must be skipped. */
    private fun destinationPathFor(entry: ZipEntry, seenPaths: MutableSet<String>, policy: SecurityPolicy): String? {
        val normalizedPath = ArchivePathAnalyzer.analyze(entry.name).normalizedPath
            ?: failValidation("archive.entry.path.invalid", "Archive entry path is invalid.", entry.name)
        if (!seenPaths.add(normalizedPath)) {
            when (policy.duplicatePolicy) {
                DuplicatePolicy.ERROR -> failValidation(
                    "archive.entry.duplicate",
                    "Archive contains duplicate entry path: $normalizedPath.",
                    entry.name
                )
                DuplicatePolicy.KEEP_FIRST -> return null
                DuplicatePolicy.KEEP_LAST -> Unit
                DuplicatePolicy.RENAME -> return renamedPath(normalizedPath, seenPaths)
            }
        }
        return normalizedPath
    }

    private fun renamedPath(path: String, seenPaths: MutableSet<String>): String {
        val parent = path.substringBeforeLast('/', "")
        val fileName = path.substringAfterLast('/')
        val baseName = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var index = 1
        while (true) {
            var candidate = if (extension.isEmpty()) "$baseName-$index" else "$baseName-$index.$extension"
            if (parent.isNotEmpty()) {
                candidate = "$parent/$candidate"
            }
            if (seenPaths.add(candidate)) {
                return candidate
            }
            check(index != Int.MAX_VALUE) { "Unable to generate unique archive entry name for $path." }
            index++
        }
    }

    private fun copyWithLimits(
        input: InputStream,
        output: OutputStream,
        entry: ZipEntry,
        totalBefore: Long,
        policy: SecurityPolicy,
        progress: ExtractionProgress,
        destinationPath: String,
        bufferSize: Int
    ): Long {
        val buffer = ByteArray(bufferSize)
        var written = 0L
        while (true) {
            progress.checkCanceled()
            val read = input.read(buffer)
            if (read < 0) {
                return written
            }
            // limits are enforced on the bytes actually inflated, not on the sizes declared in the archive
            val entryTotal = checkedAddEntry(written, read.toLong(), policy, entry.name)
            checkedAddTotal(totalBefore, entryTotal, policy, entry.name)
            output.write(buffer, 0, read)
            progress.bytesWritten(read.toLong(), destinationPath)
            written = entryTotal
        }
    }

    private fun knownUncompressedSize(entries: List<ArchiveEntryInfo>): Long {
        return entries.asSequence()
            .filterNot { it.isDirectory }
            .map { it.uncompressedSize }
            .filter { it > 0 }
            .fold(0L) { total, size -> total.saturatingPlus(size) }
    }

    private fun checkedAddEntry(current: Long, delta: Long, policy: SecurityPolicy, entryName: String): Long {
        if (Long.MAX_VALUE - current < delta) {
            failValidation("archive.entry.size.overflow", "Archive entry size overflows Long.", entryName)
        }
        val next = current + delta
        if (next > policy.maxSingleFileSize) {
            failValidation(
                "archive.entry.size.too_large",
                "Archive entry exceeded ${policy.maxSingleFileSize} bytes while extracting.",
                entryName
            )
        }
        return next
    }

    private fun checkedAddTotal(current: Long, delta: Long, policy: SecurityPolicy, entryName: String): Long {
        if (Long.MAX_VALUE - current < delta) {
            failValidation("archive.total_size.overflow", "Archive total size overflows Long.", entryName)
        }
        val next = current + delta
        if (next > policy.maxTotalUncompressedSize) {
            failValidation(
                "archive.total_size.too_large",
                "Archive exceeded ${policy.maxTotalUncompressedSize} total bytes while extracting.",
                entryName
            )
        }
        return next
    }

    private fun validateRuntimeCompressionRatio(entry: ZipEntry, written: Long, policy: SecurityPolicy) {
        if (entry.compressedSize < 0 || written <= 0) {
            return
        }
        val ratio = if (entry.compressedSize == 0L) Double.POSITIVE_INFINITY else written.toDouble() / entry.compressedSize
        if (ratio > policy.maxCompressionRatio) {
            failValidation(
                "archive.entry.compression_ratio.too_high",
                "Archive entry compression ratio is $ratio, limit is ${policy.maxCompressionRatio}.",
                entry.name
            )
        }
    }

    private fun failValidation(code: String, message: String, path: String): Nothing {
        throw ArchiveValidationException(
            ValidationReport(
                format = ArchiveFormat.ZIP,
                issues = listOf(ArchiveIssue(ArchiveIssueSeverity.ERROR, code, message, path))
            )
        )
    }

    private class ExtractionProgress(
        private val options: ArchiveExtractionOptions,
        private val totalEntries: Long,
        private val totalBytes: Long
    ) {
        private var entriesProcessed = 0L
        private var bytesWritten = 0L

        fun checkCanceled() {
            if (options.cancellationToken.isCancellationRequested()) {
                throw ArchiveOperationCanceledException()
            }
        }

        fun bytesWritten(count: Long, currentEntry: String) {
            bytesWritten += count
            emit(currentEntry)
        }

        fun entryCompleted(currentEntry: String) {
            entriesProcessed++
            emit(currentEntry)
        }

        fun emit(currentEntry: String?) {
            options.progressListener?.onProgress(entriesProcessed, totalEntries, bytesWritten, totalBytes, currentEntry)
        }
    }
}
