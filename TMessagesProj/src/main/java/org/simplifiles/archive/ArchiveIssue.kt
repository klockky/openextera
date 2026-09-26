package org.simplifiles.archive

data class ArchiveIssue @JvmOverloads constructor(
    val severity: ArchiveIssueSeverity,
    val code: String,
    val message: String,
    val path: String? = null
)
