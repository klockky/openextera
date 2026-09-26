package org.telegram.tasks

/**
 * Android resource directories use the legacy ISO 639 codes for a few languages
 * (values-iw, values-in, values-ji), while java.util.Locale may report either form
 * depending on the platform version. Localization assets are always named after the
 * modern code; the legacy one is kept as an alias for runtime lookup.
 */
object LanguageCodes {

    private val LEGACY_TO_MODERN = mapOf(
        "iw" to "he",
        "in" to "id",
        "ji" to "yi"
    )

    fun normalize(language: String): String {
        return LEGACY_TO_MODERN[language] ?: language
    }

    fun legacyAliases(language: String): List<String> {
        return LEGACY_TO_MODERN.filterValues { it == language }.keys.sorted()
    }
}
