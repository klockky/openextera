package com.exteragram.messenger.utils.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import com.exteragram.messenger.ExteraConfig
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.BaseCell
import org.telegram.ui.Components.ScaleStateListAnimator
import java.util.WeakHashMap
import java.util.function.Consumer

object UIUtil {

    private val scaleAnimatorRelayoutListeners = WeakHashMap<View, View.OnLayoutChangeListener>()

    // (x offset, y offset, size, alpha) for every icon of the "now playing" background pattern, in dp
    private val nowPlayingPattern = floatArrayOf(
        -5.5f, 20f, 20f, 0.35f,
        -5.5f, -20f, 20f, 0.35f,
        -36f, -42f, 22f, 0.375f,
        -36f, 0f, 25f, 0.425f,
        -36f, 42f, 22f, 0.375f,
        -70f, 22f, 23f, 0.35f,
        -70f, -22f, 23f, 0.35f,
        -99f, 46f, 21f, 0.275f,
        -99f, 0f, 22f, 0.325f,
        -99f, -46f, 21f, 0.275f,
        -128f, -23f, 20f, 0.225f,
        -128f, 23f, 20f, 0.225f
    )

    @JvmStatic
    fun createBottomFade(color: Int): GradientDrawable = createFade(color, GradientDrawable.Orientation.TOP_BOTTOM)

    @JvmStatic
    fun createTopFade(color: Int): GradientDrawable = createFade(color, GradientDrawable.Orientation.BOTTOM_TOP)

    private fun createFade(color: Int, orientation: GradientDrawable.Orientation) =
        GradientDrawable(orientation, intArrayOf(ColorUtils.setAlphaComponent(color, 0), ColorUtils.setAlphaComponent(color, 60), color))

    /**
     * Applies [ScaleStateListAnimator] to [view] and keeps its ripple mask in sync with the press scale,
     * so the ripple corners stay at [radius] (or [innerRadius] for non-rounded edges) while pressed.
     * Re-applies itself whenever the view size changes.
     */
    @JvmStatic
    fun applyScaleStateListAnimator(
        view: View,
        radius: Float,
        roundTop: Boolean,
        roundBottom: Boolean,
        innerRadius: Int,
        scale: Float,
        tension: Float
    ) {
        scaleAnimatorRelayoutListeners.remove(view)?.let { view.removeOnLayoutChangeListener(it) }
        view.stateListAnimator?.jumpToCurrentState()

        val width = view.width
        val height = view.height
        val relayoutListener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.width != width || v.height != height) {
                applyScaleStateListAnimator(v, radius, roundTop, roundBottom, innerRadius, scale, tension)
            }
        }
        view.addOnLayoutChangeListener(relayoutListener)
        scaleAnimatorRelayoutListeners[view] = relayoutListener

        val maxSideDp = maxOf(width, height) / AndroidUtilities.density
        val actualScale = if (maxSideDp > 0f) minOf(scale, 8f / maxSideDp) else scale

        val onProgress: (Float) -> Unit = { progress ->
            val ripple = (view.background as? BaseCell.RippleDrawableSafe)?.mask as? Theme.RippleRadMaskDrawable
            if (ripple != null) {
                val widthDp = view.width / AndroidUtilities.density
                val heightDp = view.height / AndroidUtilities.density
                val currentScale = 1f - actualScale * progress
                val paddingX = (((4f - widthDp * actualScale / 2f) * progress) / currentScale).coerceAtLeast(0f)
                val paddingY = (((4f - heightDp * actualScale / 2f) * progress) / currentScale).coerceAtLeast(0f)
                ripple.setPadding(paddingX, paddingY, paddingX, paddingY)
                val outerRadius = (radius - innerRadius * progress) / currentScale
                val nearRadius = ((radius - innerRadius) * progress) / currentScale
                ripple.setRadius(if (roundTop) outerRadius else nearRadius, if (roundBottom) outerRadius else nearRadius)
            }
        }
        ScaleStateListAnimator.apply(view, actualScale, tension, Consumer { onProgress(it) }, Consumer { onProgress(it) })
        view.stateListAnimator?.jumpToCurrentState()
        onProgress(if (view.isPressed) 1f else 0f)
    }

    @JvmStatic
    fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    @JvmStatic
    fun getFabSquareCornerRadiusDp(sizeDp: Int): Float = Math.ceil((sizeDp * 16 / 56f).toDouble()).toFloat()

    @JvmStatic
    fun createFabBackground(sizeDp: Int, color: Int, pressedColor: Int): Drawable {
        val (background, selector) = if (sizeDp == 40) {
            val windowBackground = Theme.getColor(Theme.key_windowBackgroundWhite)
            ColorUtils.blendARGB(windowBackground, Color.WHITE, 0.1f) to
                Theme.blendOver(windowBackground, Theme.getColor(Theme.key_listSelector))
        } else {
            color to pressedColor
        }
        val radius = if (ExteraConfig.squareFab) getFabSquareCornerRadiusDp(sizeDp) else sizeDp / 2f
        return Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(radius), background, selector)
    }

    fun adjustHsl(color: Int, lightness: Float, saturation: Float = -1f): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        if (saturation > 0f) {
            hsl[1] = (hsl[1] * saturation).coerceAtMost(1f)
        }
        hsl[2] = (hsl[2] * lightness).coerceAtMost(1f)
        return ColorUtils.HSLToColor(hsl)
    }

    fun drawNowPlayingPattern(canvas: Canvas, drawable: Drawable, x: Float, height: Float, alpha: Float) {
        if (alpha <= 0f) {
            return
        }
        val centerY = height / 2f
        for (i in nowPlayingPattern.indices step 4) {
            val offsetX = AndroidUtilities.dpf2(nowPlayingPattern[i])
            val offsetY = AndroidUtilities.dpf2(nowPlayingPattern[i + 1])
            val halfSize = AndroidUtilities.dpf2(nowPlayingPattern[i + 2]) / 2f
            val iconAlpha = nowPlayingPattern[i + 3]
            drawable.setBounds(
                (offsetX + x - halfSize).toInt(),
                (offsetY + centerY - halfSize).toInt(),
                (offsetX + x + halfSize).toInt(),
                (centerY + offsetY + halfSize).toInt()
            )
            drawable.alpha = (255f * alpha * iconAlpha).toInt()
            drawable.draw(canvas)
        }
    }
}
