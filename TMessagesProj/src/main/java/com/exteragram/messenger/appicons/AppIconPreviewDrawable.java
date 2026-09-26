package com.exteragram.messenger.appicons;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.exteragram.messenger.preferences.utils.IconShapeHelper;

import org.telegram.messenger.AndroidUtilities;

public class AppIconPreviewDrawable extends Drawable implements AppIconPreviewLoader.Callback {

    private final AppIcon icon;
    private final int size;
    private final Path path = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();

    private Bitmap bitmap;
    private BitmapShader shader;

    public AppIconPreviewDrawable(AppIcon icon, int size) {
        this.icon = icon;
        this.size = size;
        float sizeDp = size / AndroidUtilities.density;
        path.set(IconShapeHelper.INSTANCE.getFinalIconShapePath(sizeDp, sizeDp, 0.28f * sizeDp));
        Bitmap cached = AppIconPreviewLoader.getCached(icon, size);
        if (cached != null) {
            bitmap = cached;
        } else {
            AppIconPreviewLoader.load(icon, size, this);
        }
    }

    @Override
    public void onPreviewReady(AppIcon icon, int size, Bitmap bitmap) {
        if (this.icon == icon && this.size == size) {
            this.bitmap = bitmap;
            shader = null;
            invalidateSelf();
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (bitmap == null) {
            return;
        }
        if (shader == null) {
            shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            paint.setShader(shader);
        }
        Rect bounds = getBounds();
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        matrix.setScale((float) size / bitmap.getWidth(), (float) size / bitmap.getHeight());
        shader.setLocalMatrix(matrix);
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
