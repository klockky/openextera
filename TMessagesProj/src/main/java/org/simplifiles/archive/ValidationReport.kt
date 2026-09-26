package org.simplifiles.archive

data class ValidationReport @JvmOverloads constructor(
    val format: ArchiveFormat? = null,
    val entries: List<ArchiveEntryInfo> = emptyList(),
    val issues: List<ArchiveIssue> = emptyList()
) {
    val isSafe: Boolean
        get() = issues.none { it.severity == ArchiveIssueSeverity.ERROR || it.severity == ArchiveIssueSeverity.BLOCKER }
}
