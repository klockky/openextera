package com.exteragram.messenger.translator.core

sealed class TranslationError {

    data class RateLimited(val retryAfterMs: Long) : TranslationError()

    data object Transient : TranslationError()

    data object LanguageUnsupported : TranslationError()

    data object Fatal : TranslationError()

    data object Cancelled : TranslationError()

    val isRetryable: Boolean
        get() = this is RateLimited || this is Transient
}
