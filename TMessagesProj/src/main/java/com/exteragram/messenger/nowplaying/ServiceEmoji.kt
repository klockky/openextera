package com.exteragram.messenger.nowplaying

enum class ServiceEmoji(val documentId: Long) {
    MUSIC(5271627010681108586L),
    SPOTIFY(5271857023359681001L),
    TELEGRAM(5325674462522144646L);

    companion object {
        @JvmStatic
        fun fromString(value: String?): ServiceEmoji =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MUSIC
    }
}
