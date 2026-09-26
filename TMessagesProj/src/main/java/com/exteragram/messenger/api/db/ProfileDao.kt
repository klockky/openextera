package com.exteragram.messenger.api.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.exteragram.messenger.api.dto.BadgeDTO
import com.exteragram.messenger.api.dto.NowPlayingInfoDTO
import com.exteragram.messenger.api.dto.ProfileDTO

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<ProfileDTO>)

    @Query("SELECT * FROM ProfileDTO")
    suspend fun getAll(): List<ProfileDTO>

    @Query("SELECT * FROM ProfileDTO WHERE id = :id")
    suspend fun getById(id: Long): ProfileDTO?

    @Query("DELETE FROM ProfileDTO WHERE id IN (:ids)")
    suspend fun deleteProfiles(ids: List<Long>): Int

    @Query("UPDATE ProfileDTO SET nowPlaying = :nowPlaying WHERE id = :id")
    suspend fun updateNowPlaying(id: Long, nowPlaying: NowPlayingInfoDTO?): Int

    @Query("UPDATE ProfileDTO SET badge = :badge WHERE id = :id")
    suspend fun updateBadge(id: Long, badge: BadgeDTO?): Int
}
