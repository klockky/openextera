package org.simplifiles.archive

data class ArchiveInspection(
    val format: ArchiveFormat,
    val entries: List<ArchiveEntryInfo>
)
