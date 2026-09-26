package com.exteragram.messenger.api.dto

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.exteragram.messenger.api.model.ProfileStatus
import com.exteragram.messenger.api.model.ProfileType

@Entity
data class ProfileDTO(
    @PrimaryKey val id: Long,
    val type: ProfileType,
    val status: ProfileStatus,
    val badge: BadgeDTO?,
    val nowPlaying: NowPlayingInfoDTO?,
    val deleted: Boolean?,
    val canChangeBadge: Boolean?
)
