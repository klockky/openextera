package com.exteragram.messenger.translator.core

sealed class ProviderResponse {

    class Success(val texts: List<String>) : ProviderResponse()

    class Failure(val error: TranslationError) : ProviderResponse()
}
