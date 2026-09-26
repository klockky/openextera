package com.exteragram.messenger.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class VerticalImageSpan extends ImageSpan {

    public VerticalImageSpan(Drawable drawable) {
        super(drawable);
    }

    public static SpannableStringBuilder createSpan(Context context, int iconRes, String text, String placeholder, int colorKey, Theme.ResourcesProvider resourcesProvider) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        ArrayList<Integer> indices = new ArrayList<>();
        int index = text.indexOf(placeholder);
        while (index >= 0) {
            indices.add(index);
            index = text.indexOf(placeholder, index + 1);
        }
        Drawable drawable = context.getDrawable(iconRes);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(colorKey, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        for (int start : indices) {
            builder.setSpan(new VerticalImageSpan(drawable), start, start + placeholder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        Rect rect = getDrawable().getBounds();
        if (fm != null) {
            Paint.FontMetricsInt fmPaint = paint.getFontMetricsInt();
            int fontHeight = fmPaint.descent - fmPaint.ascent;
            int drHeight = rect.bottom - rect.top;
            int centerY = fmPaint.ascent + fontHeight / 2;

            fm.ascent = centerY - drHeight / 2;
            fm.top = fm.ascent;
            fm.bottom = centerY + drHeight / 2;
            fm.descent = fm.bottom;
        }
        return rect.right;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        Drawable drawable = getDrawable();
        canvas.save();
        Paint.FontMetricsInt fmPaint = paint.getFontMetricsInt();
        int fontHeight = fmPaint.descent - fmPaint.ascent;
        int centerY = y + fmPaint.descent - fontHeight / 2;
        int transY = centerY - (drawable.getBounds().bottom - drawable.getBounds().top) / 2;
        canvas.translate(x, transY);
        if (LocaleController.isRTL) {
            canvas.scale(-1, 1, drawable.getIntrinsicWidth() >> 1, drawable.getIntrinsicHeight() >> 1);
        }
        drawable.draw(canvas);
        canvas.restore();
    }
}
