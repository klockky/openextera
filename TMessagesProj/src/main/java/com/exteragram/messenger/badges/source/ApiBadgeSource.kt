package com.exteragram.messenger.badges.source

import com.exteragram.messenger.api.db.ProfileDao
import com.exteragram.messenger.api.dto.BadgeDTO
import com.exteragram.messenger.api.model.ProfileStatus
import java.util.concurrent.ConcurrentHashMap

class ApiBadgeSource(private val profileDao: ProfileDao) {
    private val cache = ConcurrentHashMap<Long, BadgeInfo>()

    @Suppress("UNUSED_PARAMETER")
    fun getBadge(id: Long, isUser: Boolean): BadgeDTO? = cache[id]?.badge

    fun isDeveloper(id: Long): Boolean = cache[id]?.status == ProfileStatus.DEVELOPER

    fun canChangeBadge(id: Long): Boolean = cache[id]?.canChangeBadge == true || isDeveloper(id)

    suspend fun updateLocalBadge(id: Long, badge: BadgeDTO?) {
        cache.computeIfPresent(id) { _, info -> info.copy(badge = badge) }
        profileDao.updateBadge(id, badge)
    }

    suspend fun loadToCache() {
        for (profile in profileDao.getAll()) {
            cache[profile.id] = BadgeInfo(profile.badge, profile.status, profile.canChangeBadge == true)
        }
    }
}
