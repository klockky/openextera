package com.exteragram.messenger.api.dto

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity
data class BoostySubscriberDTO(
    @PrimaryKey val id: Long,
    val name: String,
    val totalAmountRub: BigDecimal,
    val totalAmountUsd: BigDecimal
)
