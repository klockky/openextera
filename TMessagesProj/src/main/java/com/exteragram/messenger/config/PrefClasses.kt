package com.exteragram.messenger.config

import com.exteragram.messenger.ExteraConfig
import com.exteragram.messenger.backup.PreferencesUtils
import com.exteragram.messenger.utils.chats.DoubleTapUtils
import kotlin.reflect.KProperty

val allDelegates: MutableList<BasePref<*>> = ArrayList()
val registeredKeys: MutableList<PreferencesUtils.BackupItem> = ArrayList()

abstract class BasePref<T>(
    private val defaultValue: T,
    private val backupKey: String?
) {
    lateinit var key: String

    @Volatile
    private var cache: T? = null

    @Volatile
    private var loaded = false

    abstract val backupClass: Class<*>

    abstract fun fetch(key: String, defaultValue: T): T

    abstract fun save(key: String, value: T)

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): BasePref<T> {
        key = backupKey ?: property.name
        registeredKeys.add(PreferencesUtils.BackupItem(key, backupClass))
        allDelegates.add(this)
        return this
    }

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (!loaded) {
            cache = fetch(key, defaultValue)
            loaded = true
        }
        return cache as T
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (loaded && cache == value) {
            return
        }
        cache = value
        loaded = true
        save(key, value)
    }

    fun invalidate() {
        loaded = false
        cache = null
    }
}

class BooleanPref(defaultValue: Boolean, backupKey: String? = null) : BasePref<Boolean>(defaultValue, backupKey) {
    override val backupClass: Class<*> = Boolean::class.javaObjectType

    override fun fetch(key: String, defaultValue: Boolean): Boolean =
        ExteraConfig.preferences.getBoolean(key, defaultValue)

    override fun save(key: String, value: Boolean) {
        ExteraConfig.editor.putBoolean(key, value).apply()
    }
}

class IntegerPref(defaultValue: Int, backupKey: String? = null) : BasePref<Int>(defaultValue, backupKey) {
    override val backupClass: Class<*> = Int::class.javaObjectType

    override fun fetch(key: String, defaultValue: Int): Int =
        ExteraConfig.preferences.getInt(key, defaultValue)

    override fun save(key: String, value: Int) {
        ExteraConfig.editor.putInt(key, value).apply()
    }
}

class SanitizedIntegerPref(defaultValue: Int, backupKey: String? = null) : BasePref<Int>(defaultValue, backupKey) {
    override val backupClass: Class<*> = Int::class.javaObjectType

    override fun fetch(key: String, defaultValue: Int): Int =
        DoubleTapUtils.sanitizeSetting(ExteraConfig.preferences.getInt(key, defaultValue))

    override fun save(key: String, value: Int) {
        ExteraConfig.editor.putInt(key, DoubleTapUtils.sanitizeSetting(value)).apply()
    }
}

class LongPref(defaultValue: Long, backupKey: String? = null) : BasePref<Long>(defaultValue, backupKey) {
    override val backupClass: Class<*> = Long::class.javaObjectType

    override fun fetch(key: String, defaultValue: Long): Long =
        ExteraConfig.preferences.getLong(key, defaultValue)

    override fun save(key: String, value: Long) {
        ExteraConfig.editor.putLong(key, value).apply()
    }
}

class FloatPref(defaultValue: Float, backupKey: String? = null) : BasePref<Float>(defaultValue, backupKey) {
    override val backupClass: Class<*> = Float::class.javaObjectType

    override fun fetch(key: String, defaultValue: Float): Float = try {
        ExteraConfig.preferences.getFloat(key, defaultValue)
    } catch (e: ClassCastException) {
        // older versions stored some of these values as ints
        ExteraConfig.preferences.getInt(key, defaultValue.toInt()).toFloat()
    } catch (e: Exception) {
        defaultValue
    }

    override fun save(key: String, value: Float) {
        ExteraConfig.editor.putFloat(key, value).apply()
    }
}

class StringPref(defaultValue: String, backupKey: String? = null) : BasePref<String>(defaultValue, backupKey) {
    override val backupClass: Class<*> = String::class.java

    override fun fetch(key: String, defaultValue: String): String =
        ExteraConfig.preferences.getString(key, defaultValue) ?: defaultValue

    override fun save(key: String, value: String) {
        ExteraConfig.editor.putString(key, value).apply()
    }
}

class NullableStringPref(defaultValue: String?, backupKey: String? = null) : BasePref<String?>(defaultValue, backupKey) {
    override val backupClass: Class<*> = String::class.java

    override fun fetch(key: String, defaultValue: String?): String? =
        ExteraConfig.preferences.getString(key, defaultValue)

    override fun save(key: String, value: String?) {
        ExteraConfig.editor.putString(key, value).apply()
    }
}

class StringSetPref(defaultValue: Set<String>, backupKey: String? = null) : BasePref<Set<String>>(defaultValue, backupKey) {
    override val backupClass: Class<*> = Set::class.java

    override fun fetch(key: String, defaultValue: Set<String>): Set<String> =
        ExteraConfig.preferences.getStringSet(key, defaultValue) ?: defaultValue

    override fun save(key: String, value: Set<String>) {
        ExteraConfig.editor.putStringSet(key, value).apply()
    }
}

class EnumPref<E : Enum<E>>(defaultValue: E, backupKey: String? = null) : BasePref<E>(defaultValue, backupKey) {
    override val backupClass: Class<*> = Int::class.javaObjectType

    override fun fetch(key: String, defaultValue: E): E {
        val ordinal = ExteraConfig.preferences.getInt(key, defaultValue.ordinal)
        return try {
            defaultValue.declaringJavaClass.enumConstants?.getOrNull(ordinal) ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    override fun save(key: String, value: E) {
        ExteraConfig.editor.putInt(key, value.ordinal).apply()
    }
}
