package com.exteragram.messenger.api.dto

import com.google.gson.annotations.SerializedName

data class BadgeDTO(
    @SerializedName("documentId") val documentId: Long,
    @SerializedName("text") var text: String?
)
