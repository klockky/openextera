package com.exteragram.messenger.api.dto

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class AddedRegDateDTO(
    @PrimaryKey val userId: Long
)
