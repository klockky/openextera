package com.exteragram.messenger.math.inline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.text.Editable
import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Components.CubicBezierInterpolator
import kotlin.math.min

/**
 * Animates the characters of a committed math result flying from their ghost positions into
 * the real text layout. While running, the real characters are hidden with [MaskSpan].
 */
class MathRevealAnimation(private val view: TextView, private val onFinished: Runnable) {

    class MaskSpan(private val owner: MathRevealAnimation) : CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(tp: TextPaint) {
            if (owner.maskSpan === this) {
                tp.alpha = 0
            }
        }
    }

    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var maskSpan: MaskSpan? = null
    private var startedAt = 0L
    private var targetLayout: Layout? = null
    private var rangeStart = -1
    private var rangeEnd = -1
    private var duration = 200f
    private var fromX = FloatArray(0)
    private var fromY = FloatArray(0)
    private var toX = FloatArray(0)
    private var toY = FloatArray(0)

    val isRunning: Boolean
        get() = rangeStart >= 0

    fun isCaretOutside(position: Int): Boolean = position < rangeStart || position > rangeEnd

    fun begin(editable: Editable, start: Int, count: Int, startX: FloatArray, startY: FloatArray) {
        rangeStart = start
        rangeEnd = start + count
        fromX = startX
        fromY = startY
        toX = FloatArray(count)
        toY = FloatArray(count)
        targetLayout = null
        startedAt = SystemClock.elapsedRealtime()
        duration = min(420f, count * 28f + 200f)
        val span = MaskSpan(this)
        maskSpan = span
        editable.setSpan(span, rangeStart, rangeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun cancel() {
        stop(false)
    }

    fun draw(canvas: Canvas, accentColor: Int) {
        val layout = view.layout
        val text = view.text
        if (layout == null || text == null || rangeEnd > text.length) {
            stop(true)
            return
        }
        val count = rangeEnd - rangeStart
        if (targetLayout !== layout) {
            targetLayout = layout
            for (i in 0 until count) {
                val offset = rangeStart + i
                toX[i] = layout.getPrimaryHorizontal(offset)
                toY[i] = layout.getLineBaseline(layout.getLineForOffset(offset)).toFloat()
            }
        }
        val elapsed = (SystemClock.elapsedRealtime() - startedAt).toFloat()
        val progress = (elapsed / duration).coerceIn(0f, 1f)
        val colorProgress = (elapsed / COLOR_DURATION).coerceIn(0f, 1f)
        val color = ColorUtils.blendARGB(accentColor, view.currentTextColor, CubicBezierInterpolator.EASE_BOTH.getInterpolation(colorProgress))
        val alpha = Color.alpha(color)
        val scalePivotOffset = view.paint.textSize * 0.32f
        paint.set(view.paint)
        paint.color = color
        for (i in 0 until count) {
            val offset = rangeStart + i
            val t = AndroidUtilities.cascade(progress, i.toFloat(), count.toFloat(), 3.5f)
            val moveProgress = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(t)
            val scaleProgress = CubicBezierInterpolator.EASE_OUT_BACK.getInterpolation(t)
            val x = AndroidUtilities.lerp(fromX[i], toX[i], moveProgress)
            val y = AndroidUtilities.lerp(fromY[i], toY[i], moveProgress)
            val scale = AndroidUtilities.lerp(1.12f, 1f, scaleProgress)
            paint.alpha = (alpha * AndroidUtilities.lerp(0.4f, 1f, moveProgress)).toInt()
            val saveCount = canvas.save()
            try {
                canvas.scale(scale, scale, x + paint.measureText(text, offset, offset + 1) / 2f, y - scalePivotOffset)
                canvas.drawText(text, offset, offset + 1, x, y, paint)
            } finally {
                canvas.restoreToCount(saveCount)
            }
        }
        if (progress < 1f || colorProgress < 1f) {
            view.invalidate()
        } else {
            stop(true)
        }
    }

    private fun stop(deferMaskRemoval: Boolean) {
        val span = maskSpan
        if (rangeStart < 0 && span == null) {
            return
        }
        rangeStart = -1
        rangeEnd = -1
        maskSpan = null
        targetLayout = null
        if (span != null) {
            if (deferMaskRemoval) {
                AndroidUtilities.runOnUIThread { removeMasks() }
            } else {
                removeMasks()
            }
        }
        onFinished.run()
        view.invalidate()
    }

    private fun removeMasks() {
        val text = view.text as? Spannable ?: return
        text.getSpans(0, text.length, MaskSpan::class.java).forEach { text.removeSpan(it) }
    }

    private companion object {
        const val COLOR_DURATION = 800f
    }
}
