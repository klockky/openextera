package com.exteragram.messenger.feed

import android.content.Context
import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.UserConfig

class FeedConfig private constructor(account: Int) {

    data class Snapshot(
        val includeArchived: Boolean,
        val excludedChannels: Set<Long>,
        val generation: Int
    )

    private val preferences: SharedPreferences =
        ApplicationLoader.applicationContext.getSharedPreferences("feedconfig$account", Context.MODE_PRIVATE)

    @Volatile
    private var excludedChannels: Set<Long> = emptySet()

    @Volatile
    var generation: Int = 0
        private set

    @Volatile
    var isIncludeArchived: Boolean = preferences.getBoolean("includeArchived", false)
        @Synchronized set(value) {
            if (field == value) {
                return
            }
            field = value
            generation++
            preferences.edit().putBoolean("includeArchived", value).apply()
        }

    init {
        excludedChannels = preferences.getStringSet("excludedChannels", null)
            ?.mapNotNullTo(HashSet()) { it.toLongOrNull() }
            ?: emptySet()
    }

    fun isExcluded(dialogId: Long): Boolean = excludedChannels.contains(dialogId)

    @Synchronized
    fun setExcluded(dialogId: Long, excluded: Boolean) {
        val set = HashSet(excludedChannels)
        val changed = if (excluded) set.add(dialogId) else set.remove(dialogId)
        if (changed) {
            applyExcluded(set)
        }
    }

    fun getExcludedSnapshot(): Set<Long> = excludedChannels

    @Synchronized
    fun removeExcluded(dialogIds: Set<Long>) {
        if (dialogIds.isEmpty()) {
            return
        }
        val set = HashSet(excludedChannels)
        if (set.removeAll(dialogIds)) {
            applyExcluded(set)
        }
    }

    @Synchronized
    fun clearExcluded() {
        if (excludedChannels.isEmpty()) {
            return
        }
        applyExcluded(emptySet())
    }

    @Synchronized
    fun excludeAll(dialogIds: Collection<Long>) {
        val set = HashSet(excludedChannels)
        if (set.addAll(dialogIds)) {
            applyExcluded(set)
        }
    }

    private fun applyExcluded(set: Set<Long>) {
        excludedChannels = set
        generation++
        preferences.edit()
            .putStringSet("excludedChannels", set.mapTo(HashSet()) { it.toString() })
            .apply()
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(isIncludeArchived, excludedChannels, generation)

    companion object {
        private val instances = arrayOfNulls<FeedConfig>(UserConfig.MAX_ACCOUNT_COUNT)
        private val lockObjects = Array(UserConfig.MAX_ACCOUNT_COUNT) { Any() }

        @JvmStatic
        fun getInstance(account: Int): FeedConfig {
            instances[account]?.let { return it }
            synchronized(lockObjects[account]) {
                return instances[account] ?: FeedConfig(account).also { instances[account] = it }
            }
        }
    }
}
