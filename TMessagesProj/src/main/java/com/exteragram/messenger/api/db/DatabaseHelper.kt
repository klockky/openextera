package com.exteragram.messenger.api.db

import com.exteragram.messenger.api.dto.AddedRegDateDTO
import com.exteragram.messenger.api.dto.BoostySubscriberDTO
import com.exteragram.messenger.api.dto.NowPlayingInfoDTO
import com.exteragram.messenger.api.dto.ProfileDTO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.telegram.messenger.FileLog
import java.util.function.Consumer
import kotlin.coroutines.cancellation.CancellationException

object DatabaseHelper {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val database: ExteraDatabase
        get() = ExteraDatabase.getInstance()

    suspend fun insertProfiles(profiles: List<ProfileDTO>): Boolean = runSafely {
        database.profileDao().insertAll(profiles)
    }

    suspend fun deleteProfiles(ids: List<Long>): Boolean = runSafely {
        database.profileDao().deleteProfiles(ids)
    }

    @JvmStatic
    fun getNowPlaying(id: Long, callback: Consumer<NowPlayingInfoDTO?>) {
        scope.launch {
            try {
                callback.accept(database.profileDao().getById(id)?.nowPlaying)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
                callback.accept(null)
            }
        }
    }

    @JvmStatic
    fun updateNowPlaying(id: Long, newNowPlaying: NowPlayingInfoDTO?, callback: Consumer<Int>) {
        scope.launch {
            try {
                callback.accept(database.profileDao().updateNowPlaying(id, newNowPlaying))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
                callback.accept(0)
            }
        }
    }

    @JvmStatic
    fun isRegDateAdded(userId: Long, callback: Consumer<Boolean>) {
        scope.launch {
            try {
                callback.accept(database.addedRegDateDao().isAdded(userId))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
                callback.accept(false)
            }
        }
    }

    @JvmStatic
    fun setRegDateAdded(userId: Long) {
        scope.launch {
            try {
                database.addedRegDateDao().insert(AddedRegDateDTO(userId))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
            }
        }
    }

    suspend fun insertBoostySubscribers(subscribers: List<BoostySubscriberDTO>): Boolean = runSafely {
        database.boostySubscriberDao().replaceSubscribers(subscribers)
    }

    @JvmStatic
    fun getBoostySubscribers(callback: Consumer<List<BoostySubscriberDTO>>) {
        scope.launch {
            try {
                callback.accept(database.boostySubscriberDao().getAll().sortedByDescending { it.totalAmountRub })
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
                callback.accept(emptyList())
            }
        }
    }

    private inline fun runSafely(block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            FileLog.e(t)
            false
        }
    }
}
