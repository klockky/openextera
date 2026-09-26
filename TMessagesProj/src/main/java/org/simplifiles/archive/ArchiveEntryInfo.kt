package org.simplifiles.archive

data class ArchiveEntryInfo(
    val path: String,
    val normalizedPath: String?,
    val isDirectory: Boolean,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val compressionMethod: Int
)
