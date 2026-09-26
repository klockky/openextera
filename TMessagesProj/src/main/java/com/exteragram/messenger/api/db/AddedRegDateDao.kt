package com.exteragram.messenger.api.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.exteragram.messenger.api.dto.AddedRegDateDTO

@Dao
interface AddedRegDateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: AddedRegDateDTO)

    @Query("SELECT EXISTS(SELECT 1 FROM AddedRegDateDTO WHERE userId = :userId)")
    suspend fun isAdded(userId: Long): Boolean
}
