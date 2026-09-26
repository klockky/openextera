package org.simplifiles.internal.archive

import org.simplifiles.archive.ArchiveEntryInfo
import org.simplifiles.archive.ArchiveInspection
import org.simplifiles.archive.ArchiveIssue
import org.simplifiles.archive.ArchiveIssueSeverity
import org.simplifiles.archive.ValidationReport
import org.simplifiles.archive.security.DuplicatePolicy
import org.simplifiles.archive.security.SecurityPolicy

internal object ArchiveValidator {

    fun validate(inspection: ArchiveInspection, policy: SecurityPolicy): ValidationReport {
        val issues = ArrayList<ArchiveIssue>()
        if (inspection.entries.size > policy.maxEntries) {
            issues += ArchiveIssue(
                ArchiveIssueSeverity.ERROR,
                "archive.entries.too_many",
                "Archive contains ${inspection.entries.size} entries, limit is ${policy.maxEntries}."
            )
        }
        validateEntries(inspection.entries, policy, issues)
        return ValidationReport(inspection.format, inspection.entries, issues)
    }

    private fun validateEntries(entries: List<ArchiveEntryInfo>, policy: SecurityPolicy, issues: MutableList<ArchiveIssue>) {
        val seenPaths = LinkedHashSet<String>()
        val filePaths = LinkedHashSet<String>()
        val fileParentPaths = LinkedHashSet<String>()
        val directoryPaths = LinkedHashSet<String>()
        var totalUncompressedSize = 0L
        var totalOverflowReported = false
        for (entry in entries) {
            val analysis = ArchivePathAnalyzer.analyze(entry.path)
            validatePath(entry, analysis, policy, issues)
            validateCompressionMethod(entry, issues)

            val normalizedPath = analysis.normalizedPath
            if (normalizedPath != null) {
                if (policy.duplicatePolicy == DuplicatePolicy.ERROR && !seenPaths.add(normalizedPath)) {
                    issues += ArchiveIssue(
                        ArchiveIssueSeverity.ERROR,
                        "archive.entry.duplicate",
                        "Archive contains duplicate entry path: $normalizedPath.",
                        entry.path
                    )
                }
                validatePathConflict(entry, normalizedPath, filePaths, fileParentPaths, directoryPaths, issues)
                if (entry.isDirectory) {
                    directoryPaths += normalizedPath
                } else {
                    filePaths += normalizedPath
                    addParentPaths(normalizedPath, fileParentPaths)
                }
            }

            if (!entry.isDirectory) {
                validateEntrySize(entry, policy, issues)
                validateCompressionRatio(entry, policy, issues)
                if (entry.uncompressedSize >= 0) {
                    if (Long.MAX_VALUE - totalUncompressedSize >= entry.uncompressedSize) {
                        totalUncompressedSize += entry.uncompressedSize
                    } else if (!totalOverflowReported) {
                        issues += ArchiveIssue(
                            ArchiveIssueSeverity.ERROR,
                            "archive.total_size.overflow",
                            "Total uncompressed size overflows Long."
                        )
                        totalOverflowReported = true
                    }
                }
            }
        }
        if (totalUncompressedSize > policy.maxTotalUncompressedSize) {
            issues += ArchiveIssue(
                ArchiveIssueSeverity.ERROR,
                "archive.total_size.too_large",
                "Archive uncompressed size is $totalUncompressedSize bytes, limit is ${policy.maxTotalUncompressedSize}."
            )
        }
    }

    private fun validatePathConflict(
        entry: ArchiveEntryInfo,
        normalizedPath: String,
        filePaths: Set<String>,
        fileParentPaths: Set<String>,
        directoryPaths: Set<String>,
        issues: MutableList<ArchiveIssue>
    ) {
        val conflicts = if (entry.isDirectory) {
            normalizedPath in filePaths || hasExistingPathAncestor(normalizedPath, filePaths)
        } else {
            normalizedPath in directoryPaths || hasExistingPathAncestor(normalizedPath, filePaths) || normalizedPath in fileParentPaths
        }
        if (conflicts) {
            issues += ArchiveIssue(
                ArchiveIssueSeverity.ERROR,
                "archive.entry.path.conflict",
                "Archive entry path conflicts with another file or directory path: $normalizedPath.",
                entry.path
            )
        }
    }

    private fun hasExistingPathAncestor(path: String, paths: Set<String>): Boolean {
        var separator = path.indexOf('/')
        while (separator >= 0) {
            if (path.substring(0, separator) in paths) {
                return true
            }
            separator = path.indexOf('/', separator + 1)
        }
        return false
    }

    private fun addParentPaths(path: String, parents: MutableSet<String>) {
        var separator = path.indexOf('/')
        while (separator >= 0) {
            parents += path.substring(0, separator)
            separator = path.indexOf('/', separator + 1)
        }
    }

    private fun validatePath(entry: ArchiveEntryInfo, analysis: ArchivePathAnalysis, policy: SecurityPolicy, issues: MutableList<ArchiveIssue>) {
        if (analysis.isEmpty) {
            issues += ArchiveIssue(ArchiveIssueSeverity.ERROR, "archive.entry.path.empty", "Archive entry path is empty.", entry.path)
        }
        if (!policy.allowAbsolutePaths && analysis.isAbsolute) {
            issues += ArchiveIssue(ArchiveIssueSeverity.ERROR, "archive.entry.path.absolute", "Archive entry path is absolute.", entry.path)
        }
        if (analysis.containsParentTraversal) {
            issues += ArchiveIssue(ArchiveIssueSeverity.ERROR, "archive.entry.path.traversal", "Archive entry path contains parent traversal.", entry.path)
        }
    }

    private fun validateCompressionMethod(entry: ArchiveEntryInfo, issues: MutableList<ArchiveIssue>) {
        if (entry.compressionMethod == METHOD_STORED || entry.compressionMethod == METHOD_DEFLATED) {
            return
        }
        issues += ArchiveIssue(
            ArchiveIssueSeverity.ERROR,
            "archive.entry.method.unsupported",
            "Archive entry uses unsupported compression method: ${entry.compressionMethod}.",
            entry.path
        )
    }

    private fun validateEntrySize(entry: ArchiveEntryInfo, policy: SecurityPolicy, issues: MutableList<ArchiveIssue>) {
        if (entry.uncompressedSize < 0) {
            issues += ArchiveIssue(ArchiveIssueSeverity.ERROR, "archive.entry.size.unknown", "Archive entry uncompressed size is unknown.", entry.path)
            return
        }
        if (entry.uncompressedSize > policy.maxSingleFileSize) {
            issues += ArchiveIssue(
                ArchiveIssueSeverity.ERROR,
                "archive.entry.size.too_large",
                "Archive entry is ${entry.uncompressedSize} bytes, limit is ${policy.maxSingleFileSize}.",
                entry.path
            )
        }
    }

    private fun validateCompressionRatio(entry: ArchiveEntryInfo, policy: SecurityPolicy, issues: MutableList<ArchiveIssue>) {
        if (entry.compressedSize < 0 || entry.uncompressedSize <= 0) {
            return
        }
        val ratio = if (entry.compressedSize == 0L) Double.POSITIVE_INFINITY else entry.uncompressedSize.toDouble() / entry.compressedSize
        if (ratio > policy.maxCompressionRatio) {
            issues += ArchiveIssue(
                ArchiveIssueSeverity.ERROR,
                "archive.entry.compression_ratio.too_high",
                "Archive entry compression ratio is $ratio, limit is ${policy.maxCompressionRatio}.",
                entry.path
            )
        }
    }

    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATED = 8
}
