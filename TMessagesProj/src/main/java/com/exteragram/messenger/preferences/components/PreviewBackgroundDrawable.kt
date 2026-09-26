package com.exteragram.messenger.preferences.components

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme

class PreviewBackgroundDrawable @JvmOverloads constructor(radiusDp: Float = 12f) : Drawable() {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rectF = RectF()
    private val radius = AndroidUtilities.dp(radiusDp).toFloat()

    private var selectionProgress = 0f

    fun setSelectionProgress(progress: Float) {
        if (selectionProgress == progress) return
        selectionProgress = progress
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        backgroundPaint.color = PreviewColors.getBackgroundColor()
        strokePaint.color = ColorUtils.blendARGB(
            PreviewColors.getOutlineColor(),
            Theme.getColor(Theme.key_windowBackgroundWhiteValueText),
            selectionProgress
        )
        strokePaint.strokeWidth = AndroidUtilities.dp(AndroidUtilities.lerp(0.5f, 2f, selectionProgress)).toFloat()
        val inset = strokePaint.strokeWidth / 2f
        rectF.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
        canvas.drawRoundRect(rectF, radius, radius, backgroundPaint)
        canvas.drawRoundRect(rectF, radius, radius, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
