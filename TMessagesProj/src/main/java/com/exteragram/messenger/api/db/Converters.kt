package com.exteragram.messenger.api.db

import androidx.room.TypeConverter
import com.exteragram.messenger.api.dto.BadgeDTO
import com.exteragram.messenger.api.dto.NowPlayingInfoDTO
import com.google.gson.Gson
import java.math.BigDecimal

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromBadgeDTO(badgeDTO: BadgeDTO?): String? = gson.toJson(badgeDTO)

    @TypeConverter
    fun toBadgeDTO(json: String?): BadgeDTO? = gson.fromJson(json, BadgeDTO::class.java)

    @TypeConverter
    fun fromNowPlayingInfoDTO(nowPlayingInfoDTO: NowPlayingInfoDTO?): String? = gson.toJson(nowPlayingInfoDTO)

    @TypeConverter
    fun toNowPlayingInfoDTO(json: String?): NowPlayingInfoDTO? = gson.fromJson(json, NowPlayingInfoDTO::class.java)

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }
}
