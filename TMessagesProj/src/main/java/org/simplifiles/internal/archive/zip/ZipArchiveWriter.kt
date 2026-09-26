package org.simplifiles.internal.archive.zip

import org.simplifiles.archive.ArchiveSaveOptions
import org.simplifiles.exception.ArchiveOperationCanceledException
import org.simplifiles.exception.ArchiveWriteException
import org.simplifiles.files.OverwritePolicy
import org.simplifiles.internal.io.SymlinkDecision
import org.simplifiles.internal.io.SymlinkSupport
import org.simplifiles.internal.saturatingPlus
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.streams.asSequence

internal object ZipArchiveWriter {

    fun write(sourceDirectory: Path, output: Path, options: ArchiveSaveOptions) {
        val source = sourceDirectory.toAbsolutePath().normalize()
        val target = output.toAbsolutePath().normalize()
        if (target.startsWith(source)) {
            throw ArchiveWriteException(output, "output path must be outside source directory")
        }
        checkCanceled(options)
        val targetExists = Files.exists(target)
        if (targetExists) {
            when (options.overwritePolicy) {
                OverwritePolicy.ERROR -> throw ArchiveWriteException(output, "output file already exists")
                OverwritePolicy.SKIP -> return
                OverwritePolicy.REPLACE -> {
                    if (Files.isDirectory(target)) {
                        throw ArchiveWriteException(output, "output path is a directory")
                    }
                }
            }
        }
        target.parent?.let { Files.createDirectories(it) }

        val directories = listDirectories(source, options)
        val files = listFiles(source, options)
        val replacing = targetExists && options.overwritePolicy == OverwritePolicy.REPLACE
        val writeTarget = if (replacing) {
            Files.createTempFile(target.parent, "${target.fileName}.", ".tmp")
        } else {
            target
        }
        val progress = SaveProgress(options, (directories.size + files.size).toLong(), totalFileSize(files))
        try {
            progress.emit(null)
            ZipOutputStream(newOutputStream(writeTarget, replacing)).use { zip ->
                zip.setLevel(options.compressionLevel)
                writeDirectories(zip, source, directories, progress, options)
                writeFiles(zip, source, files, progress, options)
            }
            if (writeTarget != target) {
                Files.move(writeTarget, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (t: Throwable) {
            Files.deleteIfExists(writeTarget)
            throw t
        }
    }

    private fun newOutputStream(path: Path, existing: Boolean): OutputStream {
        return if (existing) {
            Files.newOutputStream(path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        } else {
            Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        }
    }

    private fun writeDirectories(zip: ZipOutputStream, root: Path, directories: List<Path>, progress: SaveProgress, options: ArchiveSaveOptions) {
        for (directory in directories) {
            progress.checkCanceled()
            val name = entryPath(root, directory) + '/'
            zip.putNextEntry(timestampedEntry(name, directory, options))
            zip.closeEntry()
            progress.entryCompleted(name)
        }
    }

    private fun writeFiles(zip: ZipOutputStream, root: Path, files: List<Path>, progress: SaveProgress, options: ArchiveSaveOptions) {
        for (file in files) {
            progress.checkCanceled()
            val name = entryPath(root, file)
            zip.putNextEntry(timestampedEntry(name, file, options))
            Files.newInputStream(file).use { input ->
                copy(input, zip, options.bufferSize, progress, name)
            }
            zip.closeEntry()
            progress.entryCompleted(name)
        }
    }

    private fun timestampedEntry(name: String, source: Path, options: ArchiveSaveOptions): ZipEntry {
        val entry = ZipEntry(name)
        entry.time = if (options.entryTimestamp == ArchiveSaveOptions.PRESERVE_SOURCE_TIMESTAMP) {
            Files.getLastModifiedTime(source).toMillis()
        } else {
            options.entryTimestamp
        }
        return entry
    }

    private fun listDirectories(root: Path, options: ArchiveSaveOptions): List<Path> {
        return Files.walk(root).use { stream ->
            stream.asSequence()
                .filter { included(it, options) }
                .filter { it != root && Files.isDirectory(it) }
                .filter { options.entryFilter.include(entryPath(root, it) + '/') }
                .sortedBy { root.relativize(it).toString() }
                .toList()
        }
    }

    private fun listFiles(root: Path, options: ArchiveSaveOptions): List<Path> {
        return Files.walk(root).use { stream ->
            stream.asSequence()
                .filter { included(it, options) }
                .filter { Files.isRegularFile(it) }
                .filter { options.entryFilter.include(entryPath(root, it)) }
                .sortedBy { root.relativize(it).toString() }
                .toList()
        }
    }

    private fun included(path: Path, options: ArchiveSaveOptions): Boolean {
        return when (SymlinkSupport.decide(path, options.symlinkPolicy)) {
            SymlinkDecision.INCLUDE -> true
            SymlinkDecision.SKIP -> false
            SymlinkDecision.FAIL -> throw ArchiveWriteException(path, "source tree contains a symbolic link")
        }
    }

    private fun entryPath(root: Path, path: Path): String = root.relativize(path).toString().replace('\\', '/')

    private fun copy(input: InputStream, output: OutputStream, bufferSize: Int, progress: SaveProgress, entryName: String) {
        val buffer = ByteArray(bufferSize)
        while (true) {
            progress.checkCanceled()
            val read = input.read(buffer)
            if (read < 0) {
                return
            }
            output.write(buffer, 0, read)
            progress.bytesWritten(read.toLong(), entryName)
        }
    }

    private fun totalFileSize(files: List<Path>): Long {
        return files.fold(0L) { total, file -> total.saturatingPlus(Files.size(file)) }
    }

    private fun checkCanceled(options: ArchiveSaveOptions) {
        if (options.cancellationToken.isCancellationRequested()) {
            throw ArchiveOperationCanceledException()
        }
    }

    private class SaveProgress(
        private val options: ArchiveSaveOptions,
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
