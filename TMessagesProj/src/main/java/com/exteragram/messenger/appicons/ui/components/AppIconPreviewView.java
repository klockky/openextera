package com.exteragram.messenger.appicons.ui.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.appicons.AppIcon;
import com.exteragram.messenger.appicons.AppIconPreviewLoader;
import com.exteragram.messenger.preferences.utils.IconShapeHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;
import java.util.Map;

public class AppIconPreviewView extends View implements AppIconPreviewLoader.Callback {

    private static final int PADDING_DP = 5;
    private static final float FADE_STEP = 16f / 220f;

    private static final Map<String, Path> shapeCache = new HashMap<>();

    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint underlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path iconPath = new Path();
    private final Rect iconBounds = new Rect();
    private final Matrix shaderMatrix = new Matrix();

    private AppIcon icon;
    private int requestedSize;
    private Bitmap bitmap;
    private BitmapShader bitmapShader;
    private boolean bitmapIsExact;
    private float bitmapAlpha;
    private Bitmap underlayBitmap;
    private BitmapShader underlayShader;
    private boolean crossfade;
    private float scale = 1f;
    private Runnable previewReadyListener;

    public AppIconPreviewView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
    }

    public void setOnPreviewReady(Runnable listener) {
        previewReadyListener = listener;
    }

    public void setCrossfade(boolean crossfade) {
        this.crossfade = crossfade;
    }

    public void setIcon(AppIcon icon) {
        if (this.icon == icon) {
            return;
        }
        this.icon = icon;
        if (crossfade && bitmap != null && bitmapAlpha >= 1f) {
            underlayBitmap = bitmap;
            underlayShader = null;
        }
        bitmap = null;
        bitmapShader = null;
        bitmapIsExact = false;
        bitmapAlpha = 0f;
        requestPreview();
        invalidate();
    }

    public void setPreviewScale(float scale) {
        if (this.scale == scale) {
            return;
        }
        this.scale = scale;
        invalidate();
    }

    private void requestPreview() {
        if (icon == null || getWidth() <= 0) {
            return;
        }
        int size = getWidth() - AndroidUtilities.dp(PADDING_DP) * 2;
        if (size <= 0) {
            return;
        }
        requestedSize = size;
        Bitmap cached = AppIconPreviewLoader.getCached(icon, size);
        if (cached != null) {
            setContent(cached, true);
            if (previewReadyListener != null) {
                previewReadyListener.run();
            }
            return;
        }
        Bitmap anyCached = AppIconPreviewLoader.getAnyCached(icon);
        if (anyCached != null) {
            setContent(anyCached, false);
        }
        AppIconPreviewLoader.load(icon, size, this);
    }

    private void setContent(Bitmap newBitmap, boolean exact) {
        if (bitmap == newBitmap) {
            bitmapIsExact |= exact;
            return;
        }
        boolean upgradeFromApproximate = bitmap != null && !bitmapIsExact && exact;
        bitmap = newBitmap;
        bitmapShader = null;
        bitmapIsExact = exact;
        if (upgradeFromApproximate) {
            return;
        }
        bitmapAlpha = !crossfade && exact && underlayBitmap == null ? 1f : 0f;
    }

    @Override
    public void onPreviewReady(AppIcon icon, int size, Bitmap bitmap) {
        if (this.icon == icon && requestedSize == size) {
            setContent(bitmap, true);
            invalidate();
            if (previewReadyListener != null) {
                previewReadyListener.run();
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updatePaths();
        requestPreview();
    }

    private void updatePaths() {
        iconPath.rewind();
        if (getWidth() <= 0) {
            return;
        }
        float sizeDp = getWidth() / AndroidUtilities.density - PADDING_DP * 2;
        if (sizeDp <= 0) {
            return;
        }
        int padding = AndroidUtilities.dp(PADDING_DP);
        Matrix matrix = new Matrix();
        matrix.setTranslate(padding, padding);
        shape(sizeDp).transform(matrix, iconPath);
        iconBounds.set(padding, padding, getWidth() - padding, getHeight() - padding);
    }

    private Path shape(float sizeDp) {
        String key = sizeDp + "_" + AndroidUtilities.density + "_" + ExteraConfig.getUseSystemIconShape();
        Path path = shapeCache.get(key);
        if (path != null) {
            return path;
        }
        path = IconShapeHelper.INSTANCE.getFinalIconShapePath(sizeDp, sizeDp, 0.28f * sizeDp);
        shapeCache.put(key, path);
        return path;
    }

    private void drawBitmap(Canvas canvas, Bitmap bitmap, BitmapShader shader, Paint paint, int alpha) {
        shaderMatrix.setTranslate(iconBounds.left, iconBounds.top);
        shaderMatrix.preScale((float) iconBounds.width() / bitmap.getWidth(), (float) iconBounds.height() / bitmap.getHeight());
        shader.setLocalMatrix(shaderMatrix);
        paint.setAlpha(alpha);
        canvas.drawPath(iconPath, paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (iconPath.isEmpty()) {
            return;
        }
        boolean scaled = scale != 1f;
        if (scaled) {
            canvas.save();
            canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
        }
        if (bitmap != null && bitmapAlpha < 1f) {
            bitmapAlpha = Math.min(1f, bitmapAlpha + FADE_STEP);
            invalidate();
        }
        if (underlayBitmap != null && bitmapAlpha < 1f) {
            if (underlayShader == null) {
                underlayShader = new BitmapShader(underlayBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                underlayPaint.setShader(underlayShader);
            }
            drawBitmap(canvas, underlayBitmap, underlayShader, underlayPaint, 255);
        }
        if (bitmap != null) {
            if (bitmapShader == null) {
                bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                bitmapPaint.setShader(bitmapShader);
            }
            drawBitmap(canvas, bitmap, bitmapShader, bitmapPaint, (int) (bitmapAlpha * 255));
            if (bitmapAlpha >= 1f) {
                underlayBitmap = null;
                underlayShader = null;
            }
        } else if (underlayBitmap == null) {
            int color = Theme.getColor(Theme.key_switchTrack, resourcesProvider);
            float pulse = (float) Math.sin((SystemClock.uptimeMillis() % 900) / 900f * Math.PI * 2) * 0.5f + 0.5f;
            placeholderPaint.setColor(ColorUtils.blendARGB(ColorUtils.setAlphaComponent(color, 56), ColorUtils.setAlphaComponent(color, 114), pulse));
            canvas.drawPath(iconPath, placeholderPaint);
            postInvalidateOnAnimation();
        }
        if (scaled) {
            canvas.restore();
        }
    }
}
