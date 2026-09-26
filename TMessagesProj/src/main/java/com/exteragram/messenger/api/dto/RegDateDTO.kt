package com.exteragram.messenger.api.dto

import com.exteragram.messenger.api.model.RegDateFlag

data class RegDateDTO(
    val timestamp: Long,
    val accuracy: Double,
    val flag: RegDateFlag,
    val date: String
)
