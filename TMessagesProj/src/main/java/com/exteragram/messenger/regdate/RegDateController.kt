package com.exteragram.messenger.regdate

import androidx.collection.LruCache
import com.exteragram.messenger.api.db.DatabaseHelper
import com.exteragram.messenger.api.dto.RegDateDTO
import com.exteragram.messenger.api.model.RegDateFlag
import com.exteragram.messenger.backup.InvisibleEncryptor
import com.exteragram.messenger.badges.BadgesController
import com.exteragram.messenger.utils.chats.ChatUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ContactsController
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import java.io.IOException
import java.util.function.Consumer

class RegDateController(val currentAccount: Int) {

    private val regDateCache = LruCache<Long, RegDateDTO>(256)

    fun fetchRegistrationDate(userId: Long, callback: Consumer<RegDateDTO?>) {
        if (!BadgesController.hasBadge()) {
            callback.accept(null)
            return
        }
        regDateCache[userId]?.let {
            callback.accept(it)
            return
        }
        // TODO(openextera): exteraSquad infrastructure (@exteraAuthBot inline request)
        ChatUtils.getInstance(currentAccount).sendBotRequest("regdate $userId", false) { result: String? ->
            AndroidUtilities.runOnUIThread {
                if (result == null || result.startsWith("failed")) {
                    callback.accept(null)
                    return@runOnUIThread
                }
                try {
                    val dto = Gson().fromJson(result, RegDateDTO::class.java)
                    regDateCache.put(userId, dto)
                    callback.accept(dto)
                } catch (e: Exception) {
                    FileLog.e(e)
                    callback.accept(null)
                }
            }
        }
    }

    fun addRegistrationDate(userId: Long, timestamp: Long, callback: Consumer<String>) {
        if (!BadgesController.isDeveloper()) {
            callback.accept("прости, но нет")
            return
        }
        DatabaseHelper.isRegDateAdded(userId) { added ->
            AndroidUtilities.runOnUIThread {
                if (added) {
                    callback.accept("ok")
                    return@runOnUIThread
                }
                ChatUtils.getInstance(currentAccount).sendBotRequest("addregdate $userId $timestamp", false) { result: String? ->
                    if (result == "ok") {
                        DatabaseHelper.setRegDateAdded(userId)
                    }
                    callback.accept(result ?: "no results")
                }
            }
        }
    }

    fun formatRegistrationDate(userId: Long, dto: RegDateDTO?): String {
        if (dto == null) {
            return getUserRegistrationDate(userId)
        }
        val date = LocaleController.formatYearMont(dto.timestamp, true)
        val name = ContactsController.formatName(MessagesController.getInstance(currentAccount).getUser(userId))
        return if (userId == UserConfig.getInstance(currentAccount).clientUserId) {
            when (dto.flag) {
                RegDateFlag.LT -> LocaleController.formatString(R.string.CreationDateSelfEarlier, date)
                RegDateFlag.ET -> LocaleController.formatString(R.string.CreationDateSelfLater, date)
                else -> LocaleController.formatString(R.string.CreationDateSelfApproximately, date)
            }
        } else {
            when (dto.flag) {
                RegDateFlag.LT -> LocaleController.formatString(R.string.CreationDateUserEarlier, name, date)
                RegDateFlag.ET -> LocaleController.formatString(R.string.CreationDateUserLater, name, date)
                else -> LocaleController.formatString(R.string.CreationDateUserApproximately, name, date)
            }
        }
    }

    fun getUserRegistrationDate(userId: Long): String {
        val registrationDate = findUserRegistrationDate(userId)
        val date = LocaleController.formatDateChat(registrationDate)
        val name = ContactsController.formatName(MessagesController.getInstance(currentAccount).getUser(userId))
        val dates = regDates!!
        val earliest = registrationDate == dates.first()
        val latest = registrationDate == dates.last()
        return if (userId == UserConfig.getInstance(currentAccount).clientUserId) {
            when {
                earliest -> LocaleController.formatString(R.string.CreationDateSelfEarlier, date)
                latest -> LocaleController.formatString(R.string.CreationDateSelfLater, date)
                else -> LocaleController.formatString(R.string.CreationDateSelfApproximately, date)
            }
        } else {
            when {
                earliest -> LocaleController.formatString(R.string.CreationDateUserEarlier, name, date)
                latest -> LocaleController.formatString(R.string.CreationDateUserLater, name, date)
                else -> LocaleController.formatString(R.string.CreationDateUserApproximately, name, date)
            }
        }
    }

    private fun findUserRegistrationDate(userId: Long): Long {
        initializeRegIds()
        val messagesController = MessagesController.getInstance(currentAccount)
        val userFull = messagesController.getUserFull(userId)
        val dialogPhotos = messagesController.getDialogPhotos(userId)

        var earliestPhotoDate = userFull?.profile_photo?.date ?: Int.MAX_VALUE
        if (dialogPhotos != null && dialogPhotos.photos.isNotEmpty()) {
            for (photo in dialogPhotos.photos) {
                if (photo != null) {
                    earliestPhotoDate = photo.date.coerceAtMost(earliestPhotoDate)
                }
            }
        }

        val ids = regIds!!
        val dates = regDates!!
        val estimated = when {
            userId < ids.first() -> dates.first()
            userId > ids.last() -> dates[ids.size - 1]
            else -> {
                val index = ids.binarySearch(userId)
                if (index >= 0) {
                    dates[index]
                } else {
                    val insertionPoint = -index - 1
                    val lowerId = ids[insertionPoint - 1]
                    val upperId = ids[insertionPoint]
                    val lowerDate = dates[insertionPoint - 1]
                    val upperDate = dates[insertionPoint]
                    // Integer division is faithful to the original implementation.
                    lowerDate + (upperDate - lowerDate) * ((userId - lowerId) / (upperId - lowerId))
                }
            }
        }
        return earliestPhotoDate.toLong().coerceAtMost(estimated)
    }

    companion object {
        private val Instance = arrayOfNulls<RegDateController>(UserConfig.MAX_ACCOUNT_COUNT)
        private val lockObjects = Array(UserConfig.MAX_ACCOUNT_COUNT) { Any() }

        private var regIds: Array<Long>? = null
        private var regDates: Array<Long>? = null

        @JvmStatic
        fun getInstance(num: Int): RegDateController {
            Instance[num]?.let { return it }
            synchronized(lockObjects[num]) {
                return Instance[num] ?: RegDateController(num).also { Instance[num] = it }
            }
        }

        private fun initializeRegIds() {
            if (regIds != null) {
                return
            }
            try {
                val bytes = ApplicationLoader.applicationContext.assets.open("extera/registration_dates.bin").use { it.readBytes() }
                val entries = Gson().fromJson(InvisibleEncryptor.decode(String(bytes, Charsets.UTF_8)), JsonObject::class.java).entrySet()
                val ids = ArrayList<Long>(entries.size)
                val dates = ArrayList<Long>(entries.size)
                for ((key, value) in entries) {
                    ids.add(key.toLong())
                    dates.add(value.asLong)
                }
                regIds = ids.toTypedArray()
                regDates = dates.toTypedArray()
            } catch (e: IOException) {
                FileLog.e(e)
            }
        }
    }
}
