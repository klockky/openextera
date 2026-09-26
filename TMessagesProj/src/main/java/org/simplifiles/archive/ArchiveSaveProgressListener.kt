package org.simplifiles.archive

// R8 stripped the callback of this unused interface; the signature mirrors the progress state the writer tracks.
fun interface ArchiveSaveProgressListener {
    fun onProgress(entriesProcessed: Long, totalEntries: Long, bytesWritten: Long, totalBytes: Long, currentEntry: String?)
}
