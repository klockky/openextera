package org.simplifiles.archive

import org.simplifiles.archive.security.SecurityPolicy
import org.simplifiles.exception.ArchiveOperationCanceledException
import org.simplifiles.exception.ArchiveValidationException
import org.simplifiles.exception.CorruptedArchiveException
import org.simplifiles.internal.archive.ArchiveFormatDetector
import org.simplifiles.internal.archive.ArchiveValidator
import org.simplifiles.internal.archive.zip.ZipArchiveExtractor
import org.simplifiles.internal.archive.zip.ZipArchiveReader
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class ArchiveSource @JvmOverloads constructor(
    val path: Path,
    val policy: SecurityPolicy = SecurityPolicy.strict()
) {

    fun withPolicy(policy: SecurityPolicy): ArchiveSource = ArchiveSource(path, policy)

    fun validate(): ValidationReport {
        unreadableFileReason()?.let { return blockerReport("archive.unreadable", it) }
        return try {
            val format = ArchiveFormatDetector.detect(path)
                ?: return blockerReport("archive.format.unsupported", "Unsupported archive format.")
            try {
                when (format) {
                    ArchiveFormat.ZIP -> ArchiveValidator.validate(ZipArchiveReader.inspect(path), policy)
                }
            } catch (e: CorruptedArchiveException) {
                ValidationReport(
                    format = format,
                    issues = listOf(ArchiveIssue(ArchiveIssueSeverity.BLOCKER, "archive.corrupted", e.message ?: "Archive is corrupted."))
                )
            }
        } catch (e: IOException) {
            blockerReport("archive.unreadable", e.message ?: "Archive cannot be read.")
        }
    }

    @Throws(IOException::class)
    fun extractTo(target: Path): ExtractedArchive = extractTo(target, ArchiveExtractionOptions.defaults())

    @Throws(IOException::class)
    fun extractTo(target: Path, options: ArchiveExtractionOptions): ExtractedArchive {
        ensureNotCanceled(options)
        val report = validate()
        if (!report.isSafe) {
            throw ArchiveValidationException(report)
        }
        ensureNotCanceled(options)
        return when (report.format) {
            ArchiveFormat.ZIP -> ZipArchiveExtractor.extract(path, target, policy, false, report.entries, options)
            null -> throw ArchiveValidationException(report)
        }
    }

    @Throws(IOException::class)
    fun extractTo(target: File): ExtractedArchive = extractTo(Paths.get(target.path))

    private fun ensureNotCanceled(options: ArchiveExtractionOptions) {
        if (options.cancellationToken.isCancellationRequested()) {
            throw ArchiveOperationCanceledException()
        }
    }

    private fun unreadableFileReason(): String? {
        if (!Files.exists(path)) {
            return "Archive file does not exist."
        }
        if (!Files.isRegularFile(path)) {
            return "Archive path is not a regular file."
        }
        return null
    }

    private fun blockerReport(code: String, message: String): ValidationReport {
        return ValidationReport(issues = listOf(ArchiveIssue(ArchiveIssueSeverity.BLOCKER, code, message)))
    }
}
