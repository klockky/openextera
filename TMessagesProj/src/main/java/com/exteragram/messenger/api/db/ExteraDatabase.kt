package com.exteragram.messenger.api.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.exteragram.messenger.api.ApiController
import com.exteragram.messenger.api.dto.AddedRegDateDTO
import com.exteragram.messenger.api.dto.BoostySubscriberDTO
import com.exteragram.messenger.api.dto.ProfileDTO
import org.telegram.messenger.ApplicationLoader

@Database(
    entities = [ProfileDTO::class, AddedRegDateDTO::class, BoostySubscriberDTO::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ExteraDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao

    abstract fun addedRegDateDao(): AddedRegDateDao

    abstract fun boostySubscriberDao(): BoostySubscriberDao

    companion object {
        @Volatile
        private var INSTANCE: ExteraDatabase? = null

        fun getInstance(): ExteraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(ApplicationLoader.applicationContext, ExteraDatabase::class.java, "extera-database")
                    .fallbackToDestructiveMigration(true)
                    .addCallback(Callback())
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private class Callback : RoomDatabase.Callback() {
            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                ApiController.resetSyncState()
            }
        }
    }
}
