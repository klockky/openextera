package com.exteragram.messenger.api.dto

import com.exteragram.messenger.api.model.NowPlayingServiceType

data class NowPlayingInfoDTO(
    val serviceType: NowPlayingServiceType,
    var username: String?
)
