package com.exteragram.messenger.math.inline

import android.graphics.Canvas
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.widget.TextView
import org.telegram.messenger.FileLog
import kotlin.math.abs
import kotlin.math.max

/**
 * Lays out the paragraph being edited with the math result inserted at the caret, so that
 * the result can be drawn as semi-transparent "ghost" text.
 */
class GhostTextLayout {

    class GhostAlphaSpan : CharacterStyle(), UpdateAppearance {
        var alpha = 1f

        override fun updateDrawState(tp: TextPaint) {
            tp.alpha = (tp.alpha * alpha).toInt()
        }
    }

    private val ghostAlpha = GhostAlphaSpan()
    private var layout: StaticLayout? = null
    private var insertOffset = 0
    private var drawTop = 0f
    private var movedText = false

    var detached = false
        private set
    var paragraphStart = 0
        private set
    var paragraphEnd = 0
        private set
    var extraHeight = 0
        private set
    var cursorShiftX = 0f
        private set
    var cursorShiftY = 0f
        private set

    fun clear() {
        layout = null
        insertOffset = 0
        drawTop = 0f
        movedText = false
        detached = false
        paragraphStart = 0
        paragraphEnd = 0
        extraHeight = 0
        cursorShiftX = 0f
        cursorShiftY = 0f
    }

    fun isEmpty(): Boolean = layout == null

    fun hasMovedText(): Boolean = movedText

    fun build(view: TextView, source: Layout, start: Int, end: Int, cursor: Int, insert: CharSequence): Boolean {
        clear()
        val text = view.text ?: return false
        if (start < 0 || end > text.length || cursor < start || cursor > end) {
            return false
        }
        val width = source.width
        if (width <= 0) {
            return false
        }
        val offset = cursor - start
        val paragraph = SpannableStringBuilder(text, start, end)
        paragraph.insert(offset, insert)
        paragraph.setSpan(ghostAlpha, offset, offset + insert.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val newLayout = newLayout(view, source, paragraph, width) ?: return false
        val firstLine = source.getLineForOffset(start)
        val lastLine = source.getLineForOffset(end)
        layout = newLayout
        insertOffset = offset
        paragraphStart = start
        paragraphEnd = end
        drawTop = source.getLineTop(firstLine).toFloat()
        extraHeight = max(0, newLayout.height - (source.getLineTop(lastLine + 1) - source.getLineTop(firstLine)))
        movedText = offset > 0 && moved(source, newLayout, start + offset - 1, offset - 1)
        cursorShiftX = newLayout.getPrimaryHorizontal(offset) - source.getPrimaryHorizontal(cursor)
        cursorShiftY = newLayout.getLineTop(newLayout.getLineForOffset(offset)) + drawTop - source.getLineTop(source.getLineForOffset(cursor))
        return true
    }

    fun buildDetached(view: TextView, source: Layout, end: Int, insert: CharSequence): Boolean {
        clear()
        val width = source.width
        if (width <= 0) {
            return false
        }
        val text = SpannableStringBuilder(insert)
        text.setSpan(ghostAlpha, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val newLayout = newLayout(view, source, text, width) ?: return false
        layout = newLayout
        detached = true
        drawTop = source.getLineBottom(source.getLineForOffset(end)).toFloat()
        extraHeight = newLayout.height
        return true
    }

    fun setAlpha(alpha: Float) {
        ghostAlpha.alpha = alpha
    }

    fun draw(canvas: Canvas) {
        val layout = layout ?: return
        canvas.save()
        canvas.translate(0f, drawTop)
        layout.draw(canvas)
        canvas.restore()
    }

    fun readInsertedPositions(skip: Int, count: Int, outX: FloatArray, outY: FloatArray) {
        val layout = layout ?: return
        for (i in 0 until count) {
            val offset = insertOffset + skip + i
            outX[i] = layout.getPrimaryHorizontal(offset)
            outY[i] = drawTop + layout.getLineBaseline(layout.getLineForOffset(offset))
        }
    }

    private fun moved(source: Layout, newLayout: StaticLayout, sourceOffset: Int, newOffset: Int): Boolean {
        val dx = abs(newLayout.getPrimaryHorizontal(newOffset) - source.getPrimaryHorizontal(sourceOffset))
        val dy = abs(newLayout.getLineTop(newLayout.getLineForOffset(newOffset)) - (source.getLineTop(source.getLineForOffset(sourceOffset)) - drawTop))
        return dx >= 0.5f || dy >= 0.5f
    }

    private fun newLayout(view: TextView, source: Layout, text: CharSequence, width: Int): StaticLayout? = try {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, view.paint, width)
            .setAlignment(source.alignment)
            .setLineSpacing(view.lineSpacingExtra, view.lineSpacingMultiplier)
            .setIncludePad(view.includeFontPadding)
            .setBreakStrategy(view.breakStrategy)
            .setHyphenationFrequency(view.hyphenationFrequency)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setJustificationMode(view.justificationMode)
        }
        if (Build.VERSION.SDK_INT >= 28) {
            builder.setUseLineSpacingFromFallbacks(view.isFallbackLineSpacing)
        }
        builder.build()
    } catch (e: Exception) {
        FileLog.e(e)
        null
    }
}
