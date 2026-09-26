package com.exteragram.messenger.translator.core

data class ProviderLimits(
    val maxTextsPerRequest: Int,
    val maxCharsPerText: Int,
    val maxCharsPerRequest: Int,
    val maxConcurrent: Int,
    val minIntervalMs: Long,
    val maxAttempts: Int,
    val baseBackoffMs: Long,
    val maxBackoffMs: Long,
) {
    companion object {
        @JvmField
        val GOOGLE = ProviderLimits(20, 5000, 8000, 2, 0, 4, 1000, 30000)

        @JvmField
        val YANDEX = ProviderLimits(20, 5000, 5000, 2, 0, 4, 1000, 30000)

        @JvmField
        val MICROSOFT = ProviderLimits(20, 5000, 10000, 4, 0, 4, 1000, 30000)
    }
}
