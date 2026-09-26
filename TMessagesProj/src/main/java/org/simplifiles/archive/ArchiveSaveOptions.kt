package org.simplifiles.archive

import org.simplifiles.files.OverwritePolicy
import org.simplifiles.files.SymlinkPolicy

class ArchiveSaveOptions @JvmOverloads constructor(
    val progressListener: ArchiveSaveProgressListener? = null,
    val cancellationToken: CancellationToken = CancellationToken.none(),
    val bufferSize: Int = 64 * 1024,
    val overwritePolicy: OverwritePolicy = OverwritePolicy.ERROR,
    val compressionLevel: Int = -1,
    val entryFilter: ArchiveEntryFilter = ArchiveEntryFilter.includeAll(),
    val symlinkPolicy: SymlinkPolicy = SymlinkPolicy.SKIP,
    val entryTimestamp: Long = PRESERVE_SOURCE_TIMESTAMP
) {

    init {
        require(bufferSize > 0) { "bufferSize must be positive." }
        require(compressionLevel in -1..9) { "compressionLevel must be between -1 and 9." }
        require(entryTimestamp == PRESERVE_SOURCE_TIMESTAMP || entryTimestamp >= 0) {
            "entryTimestamp must be PRESERVE_SOURCE_TIMESTAMP or a non-negative epoch millisecond value."
        }
    }

    override fun toString(): String {
        return "ArchiveSaveOptions(bufferSize=$bufferSize, overwritePolicy=$overwritePolicy, compressionLevel=$compressionLevel, " +
            "symlinkPolicy=$symlinkPolicy, entryTimestamp=$entryTimestamp, hasProgressListener=${progressListener != null})"
    }

    class Builder @JvmOverloads constructor(options: ArchiveSaveOptions = defaults()) {
        private var progressListener = options.progressListener
        private var cancellationToken = options.cancellationToken
        private var bufferSize = options.bufferSize
        private var overwritePolicy = options.overwritePolicy
        private var compressionLevel = options.compressionLevel
        private var entryFilter = options.entryFilter
        private var symlinkPolicy = options.symlinkPolicy
        private var entryTimestamp = options.entryTimestamp

        fun progressListener(value: ArchiveSaveProgressListener?): Builder = apply { progressListener = value }

        fun cancellationToken(value: CancellationToken): Builder = apply { cancellationToken = value }

        fun bufferSize(value: Int): Builder = apply { bufferSize = value }

        fun overwritePolicy(value: OverwritePolicy): Builder = apply { overwritePolicy = value }

        fun compressionLevel(value: Int): Builder = apply { compressionLevel = value }

        fun entryFilter(value: ArchiveEntryFilter): Builder = apply { entryFilter = value }

        fun symlinkPolicy(value: SymlinkPolicy): Builder = apply { symlinkPolicy = value }

        fun entryTimestamp(value: Long): Builder = apply { entryTimestamp = value }

        fun build(): ArchiveSaveOptions = ArchiveSaveOptions(
            progressListener,
            cancellationToken,
            bufferSize,
            overwritePolicy,
            compressionLevel,
            entryFilter,
            symlinkPolicy,
            entryTimestamp
        )
    }

    companion object {
        const val PRESERVE_SOURCE_TIMESTAMP = -1L

        fun defaults(): ArchiveSaveOptions = ArchiveSaveOptions()

        fun builder(): Builder = Builder()
    }
}
