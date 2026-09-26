package org.simplifiles.internal.archive

internal data class ArchivePathAnalysis(
    val normalizedPath: String?,
    val isEmpty: Boolean,
    val isAbsolute: Boolean,
    val containsParentTraversal: Boolean
)
