package com.exteragram.messenger.pillstack.ui.pills.crypto.utils;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class ColoredBackground extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ColoredBackground() {
        this(0xFF1BA4ED, 0xFF1488E1);
    }

    public ColoredBackground(int topColor, int bottomColor) {
        paint.setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(28), new int[]{topColor, bottomColor}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dp(1));
        strokePaint.setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(28), new int[]{0x4DFFFFFF, 0, 0x1AFFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float radius = AndroidUtilities.dp(14);
        RectF rect = AndroidUtilities.rectTmp;
        rect.set(getBounds());
        canvas.drawRoundRect(rect, radius, radius, paint);
        if (!Theme.isCurrentThemeDark() || Theme.isCurrentThemeMonet()) {
            return;
        }
        float strokeWidth = AndroidUtilities.dp(1);
        strokePaint.setStrokeWidth(strokeWidth);
        rect.inset(strokeWidth / 2f, strokeWidth / 2f);
        canvas.drawRoundRect(rect, radius, radius, strokePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        strokePaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
