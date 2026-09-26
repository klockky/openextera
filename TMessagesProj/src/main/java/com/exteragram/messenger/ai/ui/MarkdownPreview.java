package com.exteragram.messenger.ai.ui;

import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TypefaceSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.CodeHighlighting;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.Components.MarkdownParser;
import org.telegram.ui.Components.QuoteSpan;
import org.telegram.ui.iv.RichMessageConvert;

import java.util.ArrayList;

public abstract class MarkdownPreview {

    public static CharSequence format(String markdown) {
        if (TextUtils.isEmpty(markdown)) {
            return "";
        }
        ArrayList<TL_iv.PageBlock> blocks = new ArrayList<>();
        try {
            MarkdownParser.parse(markdown, blocks);
        } catch (Throwable e) {
            FileLog.e(e);
            blocks.clear();
        }
        if (blocks.isEmpty()) {
            return markdown;
        }
        CharSequence text = RichMessageConvert.blocksToCharSequence(blocks);
        if (TextUtils.isEmpty(text)) {
            return markdown;
        }
        if (text instanceof Spannable) {
            Spannable spannable = (Spannable) text;
            for (CodeHighlighting.Span span : spannable.getSpans(0, spannable.length(), CodeHighlighting.Span.class)) {
                int start = spannable.getSpanStart(span);
                int end = spannable.getSpanEnd(span);
                spannable.removeSpan(span);
                spannable.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        AndroidUtilities.removeSpans(text, QuoteSpan.QuoteStyleSpan.class);
        AndroidUtilities.removeSpans(text, QuoteSpan.class);
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end == text.length() ? text : text.subSequence(0, end);
    }
}
