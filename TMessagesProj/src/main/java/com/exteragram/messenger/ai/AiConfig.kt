@file:JvmName("AiConfig")

package com.exteragram.messenger.ai

import android.content.SharedPreferences
import com.exteragram.messenger.ExteraConfig
import com.exteragram.messenger.ai.data.Message
import com.exteragram.messenger.ai.data.Role
import com.exteragram.messenger.ai.data.Service
import com.exteragram.messenger.ai.data.Suggestions
import com.exteragram.messenger.backup.PreferencesUtils
import com.exteragram.messenger.config.BooleanPref
import com.exteragram.messenger.config.IntegerPref
import com.exteragram.messenger.config.NullableStringPref
import com.exteragram.messenger.config.StringPref
import com.google.gson.reflect.TypeToken
import org.telegram.messenger.FileLog
import org.telegram.messenger.UserConfig

@JvmField
val DEFAULT_SERVICE = Service("default", "https://generativelanguage.googleapis.com/v1beta", "gemini-3.5-flash", null)

@JvmField
val ON_DEVICE_SERVICE = Service("on-device", null, "Gemini Nano", null).apply {
    kind = Service.Kind.ON_DEVICE
}

val preferences: SharedPreferences = PreferencesUtils.getPreferences("aiConfig")

val editor: SharedPreferences.Editor by lazy { preferences.edit() }

private val legacyConfigMigrated: Unit = migrateLegacyConfig()

fun ensureConfigMigrated() {
    legacyConfigMigrated
}

private fun migrateLegacyConfig() {
    val legacyPreferences = PreferencesUtils.getPreferences("pillstackconfig")
    val newEditor = preferences.edit()
    val migratedKeys = ArrayList<String>()
    for (key in arrayOf("services", "roles", "history")) {
        if (preferences.contains(key)) {
            continue
        }
        val value = legacyPreferences.all[key] as? String ?: continue
        newEditor.putString(key, value)
        migratedKeys.add(key)
    }
    if (migratedKeys.isEmpty() || !newEditor.commit()) {
        return
    }
    val legacyEditor = legacyPreferences.edit()
    for (key in migratedKeys) {
        legacyEditor.remove(key)
    }
    legacyEditor.apply()
}

var saveHistory by BooleanPref(true)
var responseStreaming by BooleanPref(true)
var temperature by IntegerPref(10)
var showResponseOnly by BooleanPref(false)
var insertAsQuote by BooleanPref(true)
var onDeviceReasoning by BooleanPref(false)
var onDevicePreviewModel by BooleanPref(false)
var onDeviceFastModel by BooleanPref(false)
var selectedServiceId: String? by NullableStringPref(null, "selectedServiceId")
private val selectedServiceHash by IntegerPref(DEFAULT_SERVICE.legacyHash, "selectedService")
var selectedRole: String by StringPref(Suggestions.values()[0].role.name)

@JvmOverloads
fun getReplaceTelegramEditor(account: Int = UserConfig.selectedAccount): Boolean =
    ExteraConfig.preferences.getBoolean("replaceTelegramEditor", !UserConfig.getInstance(account).isPremium)

fun setReplaceTelegramEditor(value: Boolean) {
    ExteraConfig.editor.putBoolean("replaceTelegramEditor", value).apply()
}

@JvmOverloads
fun getReplaceTelegramSummaries(account: Int = UserConfig.selectedAccount): Boolean =
    ExteraConfig.preferences.getBoolean("replaceTelegramSummaries", !UserConfig.getInstance(account).isPremium)

fun setReplaceTelegramSummaries(value: Boolean) {
    ExteraConfig.editor.putBoolean("replaceTelegramSummaries", value).apply()
}

fun getSelectedService(): Service {
    val services = getServices()
    val selectedId = selectedServiceId
    if (!selectedId.isNullOrEmpty()) {
        services.firstOrNull { it.id == selectedId }?.let { return it }
        selectedServiceId = null
    }
    if (ExteraConfig.preferences.contains("selectedService")) {
        val legacyHash = selectedServiceHash
        val legacyService = services.firstOrNull { it.legacyHash == legacyHash }
        if (legacyService != null) {
            selectedServiceId = legacyService.id
        }
        ExteraConfig.editor.remove("selectedService").apply()
        if (legacyService != null) {
            return legacyService
        }
    }
    return services.firstOrNull() ?: DEFAULT_SERVICE
}

fun setSelectedServices(service: Service) {
    selectedServiceId = service.id
    ExteraConfig.editor.remove("selectedService").apply()
}

fun clearSelectedService() {
    selectedServiceId = null
    ExteraConfig.editor.remove("selectedService").apply()
}

fun getServices(): ArrayList<Service> {
    val json = preferences.getString("services", null) ?: return ArrayList()
    return try {
        val type = object : TypeToken<ArrayList<Service>>() {}.type
        val services: ArrayList<Service> = ExteraConfig.GSON.fromJson(json, type) ?: ArrayList()
        var changed = false
        for (service in services) {
            if (service.ensureId()) {
                changed = true
            }
        }
        if (changed) {
            saveServices(services)
        }
        services
    } catch (e: Exception) {
        FileLog.e(e)
        ArrayList()
    }
}

fun saveServices(services: ArrayList<Service>) {
    editor.putString("services", ExteraConfig.GSON.toJson(services)).apply()
}

fun saveRoles(roles: ArrayList<Role>) {
    editor.putString("roles", ExteraConfig.GSON.toJson(roles)).apply()
}

fun getRoles(): ArrayList<Role> {
    val json = preferences.getString("roles", null) ?: return ArrayList()
    return try {
        val type = object : TypeToken<ArrayList<Role>>() {}.type
        ExteraConfig.GSON.fromJson(json, type) ?: ArrayList()
    } catch (e: Exception) {
        FileLog.e(e)
        ArrayList()
    }
}

fun setSelectedAiRole(role: Role) {
    selectedRole = role.name
}

fun getConversationHistory(): ArrayList<Message> {
    val json = preferences.getString("history", null) ?: return ArrayList()
    return try {
        val type = object : TypeToken<ArrayList<Message>>() {}.type
        ExteraConfig.GSON.fromJson(json, type) ?: ArrayList()
    } catch (e: Exception) {
        FileLog.e(e)
        ArrayList()
    }
}

fun saveConversationHistory(history: ArrayList<Message>) {
    editor.putString("history", ExteraConfig.GSON.toJson(history)).apply()
}

fun clearConversationHistory() {
    editor.remove("history").apply()
}

fun removeLastFromHistory() {
    val history = getConversationHistory()
    if (history.size >= 2) {
        history.removeAt(history.size - 1)
        history.removeAt(history.size - 1)
        saveConversationHistory(history)
    }
}
