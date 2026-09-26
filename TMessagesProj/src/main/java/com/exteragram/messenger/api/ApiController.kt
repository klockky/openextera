package com.exteragram.messenger.api

import android.content.SharedPreferences
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.exteragram.messenger.ExteraConfig
import com.exteragram.messenger.api.db.DatabaseHelper
import com.exteragram.messenger.api.network.ApiClient
import com.exteragram.messenger.api.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.ui.Components.ForegroundDetector
import retrofit2.Response
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

object ApiController {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val syncMutex = Mutex()

    private const val SYNC_TIMESTAMP_HEADER = "X-Sync-Timestamp"
    private const val SYNC_TIMESTAMP_KEY = "lastSyncTimestamp"
    private const val PROFILES_ETAG_KEY = "profilesEtag"
    private const val PROFILES_SYNC_TIME_KEY = "lastProfilesSyncTime"
    private const val BOOSTY_SYNC_TIME_KEY = "lastBoostySyncTime"
    private const val SYNC_FAILURE_TIME_KEY = "lastSyncFailureTime"

    private val PROFILES_SYNC_INTERVAL = Duration.ofMinutes(10).toMillis()
    private val BOOSTY_SYNC_INTERVAL = Duration.ofMinutes(40).toMillis()
    private val FAILURE_BACKOFF = Duration.ofMinutes(5).toMillis()

    @JvmStatic
    fun init() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        sync()
        scheduleWorker()
        ForegroundDetector.getInstance().addListener(object : ForegroundDetector.Listener {
            override fun onBecameForeground() {
                sync()
            }

            override fun onBecameBackground() {
            }
        })
    }

    @JvmStatic
    @JvmOverloads
    fun sync(prefs: SharedPreferences = ExteraConfig.preferences, force: Boolean = false) {
        if (!ApiClient.requestsEnabled) {
            return
        }
        if (!force) {
            val now = System.currentTimeMillis()
            if (elapsedSince(now, prefs.getLong(SYNC_FAILURE_TIME_KEY, 0L)) < FAILURE_BACKOFF) {
                return
            }
            val profilesDue = elapsedSince(now, prefs.getLong(PROFILES_SYNC_TIME_KEY, 0L)) >= PROFILES_SYNC_INTERVAL
            val boostyDue = elapsedSince(now, prefs.getLong(BOOSTY_SYNC_TIME_KEY, 0L)) >= BOOSTY_SYNC_INTERVAL
            if (!profilesDue && !boostyDue) {
                return
            }
        }
        scope.launch {
            performSync(prefs, force)
        }
    }

    suspend fun performSync(prefs: SharedPreferences = ExteraConfig.preferences, force: Boolean = false): Boolean = syncMutex.withLock {
        if (!ApiClient.requestsEnabled) {
            return@withLock true
        }
        val now = System.currentTimeMillis()
        if (!force && elapsedSince(now, prefs.getLong(SYNC_FAILURE_TIME_KEY, 0L)) < FAILURE_BACKOFF) {
            return@withLock false
        }
        val syncBoosty = force || elapsedSince(now, prefs.getLong(BOOSTY_SYNC_TIME_KEY, 0L)) >= BOOSTY_SYNC_INTERVAL
        val syncProfiles = force || elapsedSince(now, prefs.getLong(PROFILES_SYNC_TIME_KEY, 0L)) >= PROFILES_SYNC_INTERVAL
        if (!syncBoosty && !syncProfiles) {
            return@withLock true
        }

        var success = true
        var attempted = false

        if (syncBoosty) {
            attempted = true
            try {
                val response = ApiClient.apiService.getBoostySubscribers()
                val subscribers = response.body()
                if (response.isSuccessful && subscribers != null) {
                    if (DatabaseHelper.insertBoostySubscribers(subscribers)) {
                        prefs.edit().putLong(BOOSTY_SYNC_TIME_KEY, now).apply()
                    } else {
                        success = false
                    }
                } else {
                    success = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
                success = false
            }
        }

        if (syncProfiles) {
            attempted = true
            try {
                val since = prefs.getString(SYNC_TIMESTAMP_KEY, null)
                val etag = prefs.getString(PROFILES_ETAG_KEY, null)
                val apiService = ApiClient.apiService
                val response = if (since == null) {
                    apiService.getAllProfiles(etag)
                } else {
                    apiService.getUpdates(since, etag)
                }
                val profiles = response.body()
                if (response.code() == 304) {
                    prefs.edit()
                        .putString(SYNC_TIMESTAMP_KEY, syncWatermark(response))
                        .putLong(PROFILES_SYNC_TIME_KEY, now)
                        .apply()
                } else if (response.isSuccessful && profiles != null) {
                    var applied = true
                    if (profiles.isNotEmpty()) {
                        val (deleted, updated) = profiles.partition { it.deleted == true }
                        if (deleted.isNotEmpty()) {
                            applied = DatabaseHelper.deleteProfiles(deleted.map { it.id }) && applied
                        }
                        if (updated.isNotEmpty()) {
                            applied = DatabaseHelper.insertProfiles(updated) && applied
                        }
                    }
                    if (applied) {
                        val editor = prefs.edit()
                        editor.putString(SYNC_TIMESTAMP_KEY, syncWatermark(response))
                        val newEtag = response.headers()["ETag"]
                        if (newEtag != null) {
                            editor.putString(PROFILES_ETAG_KEY, newEtag)
                        } else {
                            editor.remove(PROFILES_ETAG_KEY)
                        }
                        editor.putLong(PROFILES_SYNC_TIME_KEY, now)
                        editor.apply()
                    } else {
                        success = false
                    }
                } else {
                    success = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
                success = false
            }
        }

        if (!ApiClient.requestsEnabled) {
            return@withLock true
        }
        if (attempted) {
            val editor = prefs.edit()
            if (success) {
                editor.remove(SYNC_FAILURE_TIME_KEY)
            } else {
                editor.putLong(SYNC_FAILURE_TIME_KEY, now)
            }
            editor.apply()
        }
        success
    }

    private fun syncWatermark(response: Response<*>): String {
        val header = response.headers()[SYNC_TIMESTAMP_HEADER]
        if (header != null) {
            try {
                return Instant.parse(header).toString()
            } catch (e: DateTimeParseException) {
                FileLog.e(e)
            }
        }
        return Instant.now().toString()
    }

    private fun elapsedSince(now: Long, timestamp: Long): Long {
        if (timestamp <= 0 || timestamp > now) {
            return Long.MAX_VALUE
        }
        return now - timestamp
    }

    @JvmStatic
    @JvmOverloads
    fun resetSyncState(prefs: SharedPreferences = ExteraConfig.preferences) {
        prefs.edit()
            .remove(SYNC_TIMESTAMP_KEY)
            .remove(PROFILES_ETAG_KEY)
            .remove(PROFILES_SYNC_TIME_KEY)
            .remove(BOOSTY_SYNC_TIME_KEY)
            .remove(SYNC_FAILURE_TIME_KEY)
            .apply()
    }

    @JvmStatic
    fun scheduleWorker() {
        try {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, FAILURE_BACKOFF, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(ApplicationLoader.applicationContext)
                .enqueueUniquePeriodicWork("api_sync_work", ExistingPeriodicWorkPolicy.UPDATE, request)
        } catch (t: Throwable) {
            FileLog.e(t)
        }
    }
}
