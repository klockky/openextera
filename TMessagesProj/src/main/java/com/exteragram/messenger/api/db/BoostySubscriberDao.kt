package com.exteragram.messenger.api.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.exteragram.messenger.api.dto.BoostySubscriberDTO

@Dao
interface BoostySubscriberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subscribers: List<BoostySubscriberDTO>)

    @Query("SELECT * FROM BoostySubscriberDTO")
    suspend fun getAll(): List<BoostySubscriberDTO>

    @Query("DELETE FROM BoostySubscriberDTO")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceSubscribers(subscribers: List<BoostySubscriberDTO>) {
        deleteAll()
        insertAll(subscribers)
    }
}
