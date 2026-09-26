package com.exteragram.messenger.utils.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.PatternMatcher
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme

object MonetUtils {

    private const val ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED"

    private val COLOR_MAP = HashMap<String, Int>()
    private val ACCENT_PREFIXES = arrayOf("a1_", "a2_", "a3_")
    private val PARAM_PATTERN = Regex("^([^(]+)\\(([^)]+)\\)?$")

    @Volatile
    private var harmonizeContextColor = 0

    private val overlayChangeReceiver = OverlayChangeReceiver()

    init {
        COLOR_MAP["mBlack"] = R.color.black
        COLOR_MAP["mWhite"] = R.color.white
        COLOR_MAP["mRed200"] = R.color.mRed200
        COLOR_MAP["mRed500"] = R.color.mRed500
        COLOR_MAP["mRed800"] = R.color.mRed800
        COLOR_MAP["mGreen200"] = R.color.mGreen200
        COLOR_MAP["mGreen500"] = R.color.mGreen500
        COLOR_MAP["mGreen800"] = R.color.mGreen800
        if (isSupported()) {
            initSystemColors()
        }
    }

    /**
     * Resolves a monet color by name, optionally with modifiers, e.g. `a1_600(a=50, s=80, l=90)`:
     * `s` blends with white, `l` blends with black, `a` sets alpha (all in percent).
     */
    @JvmStatic
    fun getColor(name: String): Int {
        if (name.isEmpty()) {
            return 0
        }
        return try {
            var colorName = name
            var alpha = 100
            var saturation = 100
            var lightness = 100
            PARAM_PATTERN.find(name)?.let { match ->
                match.groups[1]?.value?.trim()?.let { colorName = it }
                match.groups[2]?.value?.split(",")?.forEach { param ->
                    val parts = param.split("=")
                    if (parts.size == 2) {
                        val value = parts[1].trim().toIntOrNull() ?: return@forEach
                        when (parts[0].trim()) {
                            "a" -> alpha = value
                            "s" -> saturation = value
                            "l" -> lightness = value
                        }
                    }
                }
            }

            val resId = COLOR_MAP[colorName]
            if (resId == null || resId == 0) {
                return 0
            }
            var color = ApplicationLoader.applicationContext.getColor(resId)
            if (saturation != 100) {
                color = ColorUtils.blendARGB(Color.WHITE, color, saturation / 100f)
            }
            if (lightness != 100) {
                color = ColorUtils.blendARGB(Color.BLACK, color, lightness / 100f)
            }
            if (alpha != 100) {
                color = ColorUtils.setAlphaComponent(color, (alpha * 2.55f).toInt())
            }
            if (colorName.startsWith("mR") || colorName.startsWith("mG")) harmonize(color) else color
        } catch (e: Exception) {
            FileLog.e(e)
            0
        }
    }

    @JvmStatic
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @JvmStatic
    fun getSystemAccentColor(index: Int, dark: Boolean): Int {
        if (!isSupported() || index < 0 || index >= ACCENT_PREFIXES.size) {
            return 0
        }
        val tone = if (dark) 200 else 600
        return getColor(ACCENT_PREFIXES[index] + tone)
    }

    @JvmStatic
    fun harmonize(color: Int): Int {
        val contextColor = getHarmonizeContextColor()
        return if (contextColor == 0) color else MaterialColors.harmonize(color, contextColor)
    }

    @JvmStatic
    fun getHarmonizeContextColor(): Int {
        if (!isSupported()) {
            return 0
        }
        val cached = harmonizeContextColor
        if (cached != 0) {
            return cached
        }
        return ApplicationLoader.applicationContext.getColor(android.R.color.system_accent1_600).also {
            harmonizeContextColor = it
        }
    }

    @JvmStatic
    fun registerReceiver(context: Context) {
        try {
            harmonizeContextColor = 0
            overlayChangeReceiver.register(context)
        } catch (e: Exception) {
            FileLog.e(e)
        }
    }

    @JvmStatic
    fun unregisterReceiver(context: Context) {
        try {
            overlayChangeReceiver.unregister(context)
        } catch (ignored: Exception) {
        }
    }

    class OverlayChangeReceiver : BroadcastReceiver() {
        private var isRegistered = false

        fun register(context: Context) {
            if (isRegistered) {
                return
            }
            val filter = IntentFilter(ACTION_OVERLAY_CHANGED)
            filter.addDataScheme("package")
            filter.addDataSchemeSpecificPart("android", PatternMatcher.PATTERN_LITERAL)
            context.registerReceiver(this, filter)
            isRegistered = true
        }

        fun unregister(context: Context) {
            if (isRegistered) {
                context.unregisterReceiver(this)
                isRegistered = false
            }
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_OVERLAY_CHANGED == intent.action) {
                harmonizeContextColor = 0
                Theme.refreshMonetColors()
                if (Theme.isCurrentThemeMonet() || Theme.isCurrentAccentMonet()) {
                    Theme.applyTheme(Theme.getActiveTheme(), Theme.isCurrentThemeNight())
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initSystemColors() {
        val palettes = mapOf(
            "a1" to intArrayOf(
                android.R.color.system_accent1_10, android.R.color.system_accent1_50, android.R.color.system_accent1_100,
                android.R.color.system_accent1_200, android.R.color.system_accent1_300, android.R.color.system_accent1_400,
                android.R.color.system_accent1_500, android.R.color.system_accent1_600, android.R.color.system_accent1_700,
                android.R.color.system_accent1_800, android.R.color.system_accent1_900
            ),
            "a2" to intArrayOf(
                android.R.color.system_accent2_10, android.R.color.system_accent2_50, android.R.color.system_accent2_100,
                android.R.color.system_accent2_200, android.R.color.system_accent2_300, android.R.color.system_accent2_400,
                android.R.color.system_accent2_500, android.R.color.system_accent2_600, android.R.color.system_accent2_700,
                android.R.color.system_accent2_800, android.R.color.system_accent2_900
            ),
            "a3" to intArrayOf(
                android.R.color.system_accent3_10, android.R.color.system_accent3_50, android.R.color.system_accent3_100,
                android.R.color.system_accent3_200, android.R.color.system_accent3_300, android.R.color.system_accent3_400,
                android.R.color.system_accent3_500, android.R.color.system_accent3_600, android.R.color.system_accent3_700,
                android.R.color.system_accent3_800, android.R.color.system_accent3_900
            ),
            "n1" to intArrayOf(
                android.R.color.system_neutral1_10, android.R.color.system_neutral1_50, android.R.color.system_neutral1_100,
                android.R.color.system_neutral1_200, android.R.color.system_neutral1_300, android.R.color.system_neutral1_400,
                android.R.color.system_neutral1_500, android.R.color.system_neutral1_600, android.R.color.system_neutral1_700,
                android.R.color.system_neutral1_800, android.R.color.system_neutral1_900
            ),
            "n2" to intArrayOf(
                android.R.color.system_neutral2_10, android.R.color.system_neutral2_50, android.R.color.system_neutral2_100,
                android.R.color.system_neutral2_200, android.R.color.system_neutral2_300, android.R.color.system_neutral2_400,
                android.R.color.system_neutral2_500, android.R.color.system_neutral2_600, android.R.color.system_neutral2_700,
                android.R.color.system_neutral2_800, android.R.color.system_neutral2_900
            )
        )
        val tones = intArrayOf(10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900)
        for ((prefix, colors) in palettes) {
            tones.forEachIndexed { index, tone -> COLOR_MAP["${prefix}_$tone"] = colors[index] }
        }
    }
}
