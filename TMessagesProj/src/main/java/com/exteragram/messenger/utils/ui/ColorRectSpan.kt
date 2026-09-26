package com.exteragram.messenger.utils.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ReplacementSpan
import org.telegram.messenger.AndroidUtilities
import kotlin.math.roundToInt

class ColorRectSpan(private val color: Int) : ReplacementSpan() {

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
        (paint.measureText(text, start, end) + offset + paint.textSize.toInt()).roundToInt()

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val textPaint = paint as TextPaint
        if (text is Spanned) {
            for (style in text.getSpans(start, end, CharacterStyle::class.java)) {
                if (style !== this) {
                    style.updateDrawState(textPaint)
                }
            }
        }
        canvas.drawText(text, start, end, x, y.toFloat(), textPaint)

        val size = textPaint.textSize * 0.9f
        val left = x + textPaint.measureText(text, start, end) + offset
        val centerY = (bottom + top) / 2f
        val radius = size * 0.285f
        colorPaint.color = color
        canvas.drawRoundRect(left, centerY - size / 2f, left + size, centerY + size / 2f, radius, radius, colorPaint)
    }

    companion object {
        private val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val offset = AndroidUtilities.dp(2f)
    }
}
