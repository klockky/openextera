package com.exteragram.messenger.api.model

import org.telegram.messenger.LocaleController
import org.telegram.messenger.R

enum class NowPlayingServiceType(val displayName: String) {
    NONE(LocaleController.getString(R.string.None)),
    LAST_FM("Last.fm"),
    STATS_FM("Stats.fm")
}
