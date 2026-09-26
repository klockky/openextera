@file:JvmName("PillStackConfig")

package com.exteragram.messenger.pillstack.core

import android.content.SharedPreferences
import com.exteragram.messenger.backup.PreferencesUtils
import com.exteragram.messenger.config.BooleanPref
import com.exteragram.messenger.config.IntegerPref
import com.exteragram.messenger.config.NullableStringPref
import com.exteragram.messenger.config.StringPref
import org.telegram.messenger.NotificationCenter

val preferences: SharedPreferences = PreferencesUtils.getPreferences("pillstackconfig")
val editor: SharedPreferences.Editor = preferences.edit()
private val sync = Any()

var configLoaded = false
    private set

var useCurrentLocation: Boolean by BooleanPref(true)
var customWeatherLocation: String? by NullableStringPref(null)
var customWeatherAddress: String? by NullableStringPref(null)
var infiniteScrolling: Boolean by BooleanPref(true)
var gramTargetCurrency: String by StringPref("AUTO")
var btcTargetCurrency: String by StringPref("AUTO")
var usdTargetCurrency: String by StringPref("AUTO")
var lastActivePillId: Int by IntegerPref(-1, "lastActivePillId")

var activePills: MutableList<Int> = ArrayList()
    private set
var hiddenPills: MutableList<Int> = ArrayList()
    private set
private val pendingUpdates = HashSet<Int>()

@Suppress("unused")
private val init: Unit = loadConfig()

private fun parsePillsList(value: String?): ArrayList<Int> {
    val result = ArrayList<Int>()
    if (value.isNullOrEmpty()) {
        return result
    }
    var list: String = value
    if (list.startsWith("[")) {
        list = list.replace(Regex("[\\[\\]\"]"), "")
    }
    for (part in list.split(",")) {
        part.trim().toIntOrNull()?.let { result.add(it) }
    }
    return result
}

private fun serializePillsList(list: List<Int>): String {
    return if (list.isEmpty()) "" else list.joinToString(",")
}

fun getDefaultActivePills(): ArrayList<Int> = ArrayList()

fun loadConfig() {
    synchronized(sync) {
        if (configLoaded) {
            return
        }
        val active = preferences.getString("activePills", null)
        val hidden = preferences.getString("hiddenPills", null)
        if (active != null) {
            activePills = parsePillsList(active).toMutableList()
            hiddenPills = if (hidden != null) parsePillsList(hidden).toMutableList() else ArrayList()
        } else {
            activePills = getDefaultActivePills().toMutableList()
            hiddenPills = ArrayList()
            for (pill in PillRegistry.getRegisteredPills()) {
                if (!activePills.contains(pill.id())) {
                    hiddenPills.add(pill.id())
                }
            }
            savePillsLayout()
        }
        sanitizePills()
        configLoaded = true
    }
}

fun reloadConfig() {
    synchronized(sync) {
        configLoaded = false
        loadConfig()
    }
}

fun sanitizePills() {
    var changed = activePills.removeAll { !PillRegistry.isRegistered(it) } ||
        hiddenPills.removeAll { !PillRegistry.isRegistered(it) }
    for (pill in PillRegistry.getRegisteredPills()) {
        if (!activePills.contains(pill.id()) && !hiddenPills.contains(pill.id())) {
            hiddenPills.add(pill.id())
            changed = true
        }
    }
    if (changed) {
        savePillsLayout()
    }
}

fun savePillsLayout() {
    editor.putString("activePills", serializePillsList(activePills))
        .putString("hiddenPills", serializePillsList(hiddenPills))
        .apply()
}

fun saveLastActivePillId(id: Int) {
    lastActivePillId = id
    editor.putInt("lastActivePillId", id).apply()
}

fun notifySettingsChanged(vararg pillIds: Int) {
    if (pillIds.isEmpty()) {
        for (pill in PillRegistry.getRegisteredPills()) {
            pendingUpdates.add(pill.id())
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackSettingsChanged)
        return
    }
    for (id in pillIds) {
        pendingUpdates.add(id)
    }
    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackSettingsChanged, *pillIds.toTypedArray())
}

fun checkAndClearPendingUpdate(id: Int): Boolean = pendingUpdates.remove(id)

fun shouldUpdatePill(args: Array<Any?>?, vararg pillIds: Int): Boolean {
    if (args.isNullOrEmpty() || pillIds.isEmpty()) {
        return true
    }
    return args.any { it is Int && it in pillIds }
}
