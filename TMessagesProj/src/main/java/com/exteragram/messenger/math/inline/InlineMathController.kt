package com.exteragram.messenger.math.inline

import android.graphics.Canvas
import android.graphics.Region
import android.text.Editable
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.style.MetricAffectingSpan
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.TextView
import com.exteragram.messenger.ExteraConfig
import com.exteragram.messenger.math.MathExpression
import com.exteragram.messenger.math.MathOptions
import com.exteragram.messenger.math.MathSuggestion
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.AnimatedFloat
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.QuoteSpan
import org.telegram.ui.Components.TextStyleSpan
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Shows the result of a math expression typed before `=` as ghost text right after the caret
 * and lets the user accept it with space / tab / right arrow or revert it with backspace.
 */
class InlineMathController(private val view: TextView, private val delegate: Delegate?) {

    interface Delegate {
        fun accentColor(): Int

        fun runProgrammatic(runnable: Runnable)
    }

    private val ghost = GhostTextLayout()
    private val appear = AnimatedFloat(view, 0, 120, CubicBezierInterpolator.EASE_OUT_QUINT)
    private val reveal = MathRevealAnimation(view) { dirty = true }
    private val announce = Runnable {
        val value = suggestion?.value ?: return@Runnable
        announcedValue = value
        AndroidUtilities.makeAccessibilityAnnouncement(LocaleController.formatString(R.string.InlineMathResultAnnouncement, value))
    }

    private var suggestion: MathSuggestion? = null
    private var announcedValue: String? = null
    private var options = MathOptions('.')
    private var optionsLocale: Locale? = null
    private var dirty = true
    private var layoutWidth = -1
    private var insertingSelf = false
    private var caretMovedByTouch = false
    private var swallowKeyCode = 0
    private var undoStart = -1
    private var undoEnd = -1
    private var suppressedAt = -1

    val extraBottom: Int
        get() = ghost.extraHeight

    fun hasCursorShift(): Boolean = ghost.hasMovedText()

    val cursorShiftX: Float
        get() = if (hasCursorShift()) ghost.cursorShiftX else 0f

    val cursorShiftY: Float
        get() = if (hasCursorShift()) ghost.cursorShiftY else 0f

    fun clipReplacedParagraph(canvas: Canvas, offsetY: Int) {
        if (ghost.isEmpty() || ghost.detached) {
            return
        }
        val layout = view.layout ?: return
        val top = offsetY + layout.getLineTop(layout.getLineForOffset(ghost.paragraphStart)).toFloat()
        val bottom = offsetY + layout.getLineBottom(layout.getLineForOffset(ghost.paragraphEnd)).toFloat()
        canvas.clipRect(0f, top, view.width.toFloat(), bottom, Region.Op.DIFFERENCE)
    }

    fun draw(canvas: Canvas, offsetX: Int, offsetY: Int, clipTop: Float, clipBottom: Float) {
        val revealing = reveal.isRunning
        if (!revealing && ghost.isEmpty()) {
            appear.set(0f, true)
            return
        }
        canvas.save()
        canvas.clipRect(0f, clipTop, view.width.toFloat(), clipBottom)
        canvas.translate(offsetX.toFloat(), offsetY.toFloat())
        if (revealing) {
            reveal.draw(canvas, delegate?.accentColor() ?: view.currentTextColor)
        } else {
            ghost.setAlpha(appear.set(1f) * GHOST_ALPHA)
            ghost.draw(canvas)
        }
        canvas.restore()
    }

    fun invalidateState() {
        if (!insertingSelf) {
            val selectionStart = view.selectionStart
            if (selectionStart != suppressedAt) {
                suppressedAt = -1
            }
            if (selectionStart != undoEnd || view.selectionEnd != undoEnd) {
                clearUndo()
            }
            if (reveal.isRunning && reveal.isCaretOutside(selectionStart)) {
                reveal.cancel()
            }
        }
        schedule()
    }

    fun onTextChanged() {
        if (!insertingSelf) {
            reveal.cancel()
            clearUndo()
            suppressedAt = -1
            caretMovedByTouch = false
        }
        schedule()
    }

    fun onTouchDown() {
        if (caretMovedByTouch) {
            return
        }
        caretMovedByTouch = true
        schedule()
    }

    fun onFocusChanged(focused: Boolean) {
        if (!focused) {
            reveal.cancel()
            clearUndo()
        }
        schedule()
    }

    fun cancel() {
        AndroidUtilities.cancelRunOnUIThread(announce)
        announcedValue = null
        reveal.cancel()
        clearUndo()
        suppressedAt = -1
        caretMovedByTouch = false
        suggestion = null
        ghost.clear()
        schedule()
    }

    /**
     * @return true if the extra height required below the text changed.
     */
    fun updateOnMeasure(): Boolean {
        val oldExtraHeight = ghost.extraHeight
        val width = view.layout?.width ?: 0
        if (width != layoutWidth) {
            layoutWidth = width
            dirty = true
        }
        update()
        if (AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
            updateAnnouncement()
        }
        return ghost.extraHeight != oldExtraHeight
    }

    private fun schedule() {
        dirty = true
        view.invalidate()
        if (ghost.extraHeight != 0 || suggestion != null || canTrigger()) {
            view.requestLayout()
        }
    }

    private fun canTrigger(): Boolean {
        if (!ExteraConfig.inlineMathResult) {
            return false
        }
        val text = view.text ?: return false
        val selectionStart = view.selectionStart
        return selectionStart in 1..text.length && text[selectionStart - 1] == '='
    }

    private fun updateAnnouncement() {
        val value = suggestion?.value
        if (value == null) {
            announcedValue = null
            AndroidUtilities.cancelRunOnUIThread(announce)
        } else if (value != announcedValue) {
            AndroidUtilities.cancelRunOnUIThread(announce)
            AndroidUtilities.runOnUIThread(announce, ANNOUNCE_DELAY)
        }
    }

    private fun options(): MathOptions {
        val locale = LocaleController.getInstance().currentLocale ?: Locale.US
        if (locale != optionsLocale) {
            optionsLocale = locale
            options = MathOptions(DecimalFormatSymbols.getInstance(locale).decimalSeparator)
        }
        return options
    }

    private fun update() {
        if (!dirty) {
            return
        }
        dirty = false
        suggestion = null
        ghost.clear()
        if (!ExteraConfig.inlineMathResult || reveal.isRunning || suppressedAt >= 0 || !view.isFocused || !view.isEnabled) {
            return
        }
        val layout = view.layout ?: return
        val text = view.text ?: return
        val cursor = view.selectionStart
        if (cursor <= 0 || cursor != view.selectionEnd || cursor > text.length) {
            return
        }
        val newSuggestion = MathExpression.suggestionAt(text, cursor, options()) ?: return
        if (text is Spannable && BaseInputConnection.getComposingSpanStart(text) != -1) {
            return
        }
        if (text is Spanned && text.getSpans(cursor - 1, cursor, MetricAffectingSpan::class.java).isNotEmpty()) {
            return
        }
        if (layout.getParagraphDirection(layout.getLineForOffset(cursor)) != Layout.DIR_LEFT_TO_RIGHT) {
            return
        }
        val paragraphStart = text.lastIndexOf('\n', cursor - 1) + 1
        val paragraphEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        val lastParagraph = paragraphEnd >= text.length
        if (hasUnsupportedSpans(text, paragraphStart, paragraphEnd)) {
            if (lastParagraph && ghost.buildDetached(view, layout, paragraphEnd, newSuggestion.insertText)) {
                suggestion = newSuggestion
            }
            return
        }
        if (!ghost.build(view, layout, paragraphStart, paragraphEnd, cursor, newSuggestion.insertText)) {
            return
        }
        val wouldShiftText = (ghost.extraHeight > 0 && !lastParagraph) || (ghost.hasMovedText() && caretMovedByTouch)
        if (wouldShiftText && !(lastParagraph && ghost.buildDetached(view, layout, paragraphEnd, newSuggestion.insertText))) {
            ghost.clear()
        } else {
            suggestion = newSuggestion
        }
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            if (swallowKeyCode == 0 || event.keyCode != swallowKeyCode) {
                return false
            }
            swallowKeyCode = 0
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0 || event.isCtrlPressed || event.isAltPressed || event.isShiftPressed) {
            return false
        }
        val handled = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SPACE -> commit()
            KeyEvent.KEYCODE_DEL -> undo()
            else -> false
        }
        if (handled) {
            swallowKeyCode = event.keyCode
        }
        return handled
    }

    fun wrap(connection: InputConnection): InputConnection = object : InputConnectionWrapper(connection, false) {
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (text != null && text.length == 1 && text[0] == ' ' && suggestion != null) {
                finishComposingText()
                if (commit()) {
                    return true
                }
            }
            return super.commitText(text, newCursorPosition)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength == 1 && afterLength == 0 && undo()) {
                return true
            }
            return super.deleteSurroundingText(beforeLength, afterLength)
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength == 1 && afterLength == 0 && undo()) {
                return true
            }
            return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
        }
    }

    private fun commit(): Boolean {
        val suggestion = suggestion ?: return false
        val editable = view.text as? Editable ?: return false
        if (suggestion.insertAt != view.selectionStart || view.selectionStart != view.selectionEnd || suggestion.insertAt > editable.length) {
            return false
        }
        val prefixLength = suggestion.insertText.length - suggestion.value.length
        val count = suggestion.value.length
        val startX = FloatArray(count)
        val startY = FloatArray(count)
        ghost.readInsertedPositions(prefixLength, count, startX, startY)
        BaseInputConnection.removeComposingSpans(editable)
        if (!edit { editable.insert(suggestion.insertAt, suggestion.insertText) }) {
            return false
        }
        val end = suggestion.insertAt + suggestion.insertText.length
        Selection.setSelection(editable, end)
        this.suggestion = null
        ghost.clear()
        dirty = true
        reveal.begin(editable, suggestion.insertAt + prefixLength, count, startX, startY)
        undoStart = suggestion.insertAt
        undoEnd = end
        appear.set(0f, true)
        view.requestLayout()
        view.invalidate()
        return true
    }

    private fun undo(): Boolean {
        if (undoStart < 0) {
            return false
        }
        val editable = view.text as? Editable ?: return false
        if (undoEnd > editable.length || view.selectionStart != undoEnd || view.selectionEnd != undoEnd) {
            clearUndo()
            return false
        }
        reveal.cancel()
        val start = undoStart
        if (!edit { editable.delete(undoStart, undoEnd) }) {
            return false
        }
        Selection.setSelection(editable, start)
        clearUndo()
        suppressedAt = start
        dirty = true
        view.requestLayout()
        view.invalidate()
        return true
    }

    private fun edit(action: () -> Unit): Boolean {
        var success = true
        val runnable = Runnable {
            insertingSelf = true
            try {
                action()
            } catch (e: Exception) {
                FileLog.e(e)
                success = false
            } finally {
                insertingSelf = false
            }
        }
        if (delegate != null) {
            delegate.runProgrammatic(runnable)
        } else {
            runnable.run()
        }
        return success
    }

    private fun hasUnsupportedSpans(text: CharSequence, start: Int, end: Int): Boolean {
        if (text !is Spanned) {
            return false
        }
        return text.getSpans(start, end, AnimatedEmojiSpan::class.java).isNotEmpty() ||
            text.getSpans(start, end, QuoteSpan::class.java).isNotEmpty() ||
            text.getSpans(start, end, TextStyleSpan::class.java).any { it.isSpoiler }
    }

    private fun clearUndo() {
        undoStart = -1
        undoEnd = -1
    }

    private companion object {
        const val GHOST_ALPHA = 0.4f
        const val ANNOUNCE_DELAY = 600L
    }
}
