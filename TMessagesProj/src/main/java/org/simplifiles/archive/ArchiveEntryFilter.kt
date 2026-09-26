package org.simplifiles.archive

fun interface ArchiveEntryFilter {

    fun include(path: String): Boolean

    companion object {
        fun includeAll(): ArchiveEntryFilter = ArchiveEntryFilter { true }
    }
}
