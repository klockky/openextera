package org.simplifiles.archive

class ArchiveExtractionOptions @JvmOverloads constructor(
    val progressListener: ArchiveProgressListener? = null,
    val cancellationToken: CancellationToken = CancellationToken.none(),
    val bufferSize: Int = 64 * 1024,
    val targetPolicy: ExtractionTargetPolicy = ExtractionTargetPolicy.ERROR_IF_NOT_EMPTY
) {

    init {
        require(bufferSize > 0) { "bufferSize must be positive." }
    }

    override fun toString(): String {
        return "ArchiveExtractionOptions(bufferSize=$bufferSize, targetPolicy=$targetPolicy, hasProgressListener=${progressListener != null})"
    }

    companion object {
        fun defaults(): ArchiveExtractionOptions = ArchiveExtractionOptions()
    }
}
