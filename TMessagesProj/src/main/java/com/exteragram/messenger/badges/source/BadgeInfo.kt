package com.exteragram.messenger.badges.source

import com.exteragram.messenger.api.dto.BadgeDTO
import com.exteragram.messenger.api.model.ProfileStatus

internal data class BadgeInfo(
    val badge: BadgeDTO?,
    val status: ProfileStatus,
    val canChangeBadge: Boolean
)
