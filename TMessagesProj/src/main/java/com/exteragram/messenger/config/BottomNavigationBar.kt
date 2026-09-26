package com.exteragram.messenger.config

object BottomNavigationBar {
    const val MODE_DEFAULT = 0
    const val MODE_HIDDEN = 1
    const val MODE_FLOATING = 2

    @JvmStatic
    var mode: Int = MODE_DEFAULT
        get() {
            field = field.coerceIn(MODE_DEFAULT, MODE_FLOATING)
            return field
        }
        set(value) {
            field = value.coerceIn(MODE_DEFAULT, MODE_FLOATING)
        }

    @JvmStatic
    fun hidden(): Boolean = mode == MODE_HIDDEN

    @JvmStatic
    fun visible(): Boolean = mode != MODE_HIDDEN

    @JvmStatic
    fun floating(): Boolean = mode == MODE_FLOATING
}
