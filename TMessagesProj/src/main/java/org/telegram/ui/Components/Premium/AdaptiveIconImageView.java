package org.telegram.ui.Components.Premium;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.preferences.utils.IconShapeHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;
import java.util.Map;

public abstract class AdaptiveIconImageView extends ImageView {

    private static final Map<String, Path> shapeCache = new HashMap<>();
    private static final Map<Integer, Drawable.ConstantState> drawableStateCache = new HashMap<>();

    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Path iconPath = new Path();
    Path outlinePath = new Path();

    private Drawable foreground;
    private boolean loading;
    private int outerPadding = AndroidUtilities.dp(5);
    private int backgroundOuterPadding = AndroidUtilities.dp(42);

    public AdaptiveIconImageView(Context context) {
        super(context);
        placeholderPaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 85));
    }

    private Drawable loadDrawable(int resId) {
        Drawable.ConstantState state;
        synchronized (drawableStateCache) {
            state = drawableStateCache.get(resId);
        }
        if (state != null) {
            return state.newDrawable(ApplicationLoader.applicationContext.getResources()).mutate();
        }
        Drawable drawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, resId);
        if (drawable == null) {
            return null;
        }
        Drawable.ConstantState newState = drawable.getConstantState();
        if (newState == null) {
            return drawable;
        }
        synchronized (drawableStateCache) {
            drawableStateCache.put(resId, newState);
        }
        return newState.newDrawable(ApplicationLoader.applicationContext.getResources()).mutate();
    }

    public void setForeground(int resId) {
        foreground = loadDrawable(resId);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updatePath();
    }

    public void setPadding(int padding) {
        setPadding(padding, padding, padding, padding);
    }

    public void setOuterPadding(int outerPadding) {
        this.outerPadding = outerPadding;
    }

    public void setBackgroundOuterPadding(int backgroundOuterPadding) {
        this.backgroundOuterPadding = backgroundOuterPadding;
    }

    @Override
    public void draw(Canvas canvas) {
        if (!iconPath.isEmpty()) {
            canvas.clipPath(iconPath);
        }
        canvas.save();
        canvas.scale((float) backgroundOuterPadding / getWidth() + 1f, (float) backgroundOuterPadding / getHeight() + 1f, getWidth() / 2f, getHeight() / 2f);
        super.draw(canvas);
        canvas.restore();
        if (loading && !iconPath.isEmpty()) {
            float t = (float) Math.sin((SystemClock.uptimeMillis() % 900) / 900f * Math.PI * 2) * 0.5f + 0.5f;
            int color = Theme.getColor(Theme.key_switchTrack);
            placeholderPaint.setColor(ColorUtils.blendARGB(ColorUtils.setAlphaComponent(color, 56), ColorUtils.setAlphaComponent(color, 114), t));
            canvas.drawPath(iconPath, placeholderPaint);
            postInvalidateOnAnimation();
        }
        if (foreground != null) {
            foreground.setBounds(-outerPadding, -outerPadding, getWidth() + outerPadding, getHeight() + outerPadding);
            foreground.draw(canvas);
        }
    }

    private void updatePath() {
        iconPath.rewind();
        outlinePath.rewind();
        float width = getWidth() / AndroidUtilities.density;
        if (width <= 0) {
            return;
        }
        float iconSize = width - getPaddingLeft() / AndroidUtilities.density * 2f;
        float outlineSize = width - 4f;
        float iconRadius = iconSize / width * 16f;
        float outlineRadius = outlineSize / width * 16f;

        String iconKey = "i_" + iconSize + "_" + iconRadius;
        Path iconShape = shapeCache.get(iconKey);
        if (iconShape == null) {
            iconShape = IconShapeHelper.INSTANCE.getFinalIconShapePath(iconSize, iconSize, iconRadius);
            shapeCache.put(iconKey, iconShape);
        }
        String outlineKey = "o_" + outlineSize + "_" + outlineRadius;
        Path outlineShape = shapeCache.get(outlineKey);
        if (outlineShape == null) {
            outlineShape = IconShapeHelper.INSTANCE.getFinalIconShapePath(outlineSize, outlineSize, outlineRadius);
            shapeCache.put(outlineKey, outlineShape);
        }

        Matrix matrix = new Matrix();
        matrix.setTranslate(getPaddingLeft(), getPaddingTop());
        iconShape.transform(matrix, iconPath);
        matrix.setTranslate(AndroidUtilities.dp(2), AndroidUtilities.dp(2));
        outlineShape.transform(matrix, outlinePath);
    }
}
