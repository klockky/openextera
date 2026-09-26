package org.simplifiles.archive.security

data class SecurityPolicy @JvmOverloads constructor(
    val maxEntries: Long = 10_000,
    val maxTotalUncompressedSize: Long = 1_000_000_000,
    val maxSingleFileSize: Long = 100_000_000,
    val maxCompressionRatio: Double = 100.0,
    val maxNestedArchiveDepth: Int = 0,
    val allowSymlinks: Boolean = false,
    val allowHardlinks: Boolean = false,
    val allowAbsolutePaths: Boolean = false,
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.ERROR
) {

    init {
        require(maxEntries > 0) { "maxEntries must be positive." }
        require(maxTotalUncompressedSize > 0) { "maxTotalUncompressedSize must be positive." }
        require(maxSingleFileSize > 0) { "maxSingleFileSize must be positive." }
        require(maxCompressionRatio > 0.0) { "maxCompressionRatio must be positive." }
        require(maxNestedArchiveDepth >= 0) { "maxNestedArchiveDepth must not be negative." }
    }

    class Builder @JvmOverloads constructor(policy: SecurityPolicy = strict()) {
        private var maxEntries = policy.maxEntries
        private var maxTotalUncompressedSize = policy.maxTotalUncompressedSize
        private var maxSingleFileSize = policy.maxSingleFileSize
        private var maxCompressionRatio = policy.maxCompressionRatio
        private var maxNestedArchiveDepth = policy.maxNestedArchiveDepth
        private var allowSymlinks = policy.allowSymlinks
        private var allowHardlinks = policy.allowHardlinks
        private var allowAbsolutePaths = policy.allowAbsolutePaths
        private var duplicatePolicy = policy.duplicatePolicy

        fun maxEntries(value: Long): Builder = apply { maxEntries = value }

        fun maxTotalUncompressedSize(value: Long): Builder = apply { maxTotalUncompressedSize = value }

        fun maxSingleFileSize(value: Long): Builder = apply { maxSingleFileSize = value }

        fun maxCompressionRatio(value: Double): Builder = apply { maxCompressionRatio = value }

        fun maxNestedArchiveDepth(value: Int): Builder = apply { maxNestedArchiveDepth = value }

        fun allowSymlinks(value: Boolean): Builder = apply { allowSymlinks = value }

        fun allowHardlinks(value: Boolean): Builder = apply { allowHardlinks = value }

        fun allowAbsolutePaths(value: Boolean): Builder = apply { allowAbsolutePaths = value }

        fun duplicatePolicy(value: DuplicatePolicy): Builder = apply { duplicatePolicy = value }

        fun build(): SecurityPolicy = SecurityPolicy(
            maxEntries,
            maxTotalUncompressedSize,
            maxSingleFileSize,
            maxCompressionRatio,
            maxNestedArchiveDepth,
            allowSymlinks,
            allowHardlinks,
            allowAbsolutePaths,
            duplicatePolicy
        )
    }

    companion object {
        fun strict(): SecurityPolicy = SecurityPolicy()

        fun builder(): Builder = Builder()
    }
}
