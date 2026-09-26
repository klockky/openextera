package com.exteragram.messenger.utils.chats;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;
import java.util.List;

public class SwipeActionsHelper {

    private static final float SPRING_SCALE = 2000f;

    private final View parent;
    private final Theme.ResourcesProvider resourcesProvider;

    private final List<SwipeAction> actions = new ArrayList<>();
    private final SparseArray<Drawable> icons = new SparseArray<>();
    private final SparseArray<Drawable> spareIcons = new SparseArray<>();
    private final SparseArray<Float> iconFits = new SparseArray<>();

    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlineDarkenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private final AnimatedFloat liveAlpha = new AnimatedFloat(this::invalidate, 0, 280, CubicBezierInterpolator.EASE_OUT);

    private final FloatValueHolder position = new FloatValueHolder(0);
    private final SpringAnimation positionSpring;
    private final FloatValueHolder visibility = new FloatValueHolder(0);
    private final SpringAnimation visibilitySpring;
    private final FloatValueHolder fill = new FloatValueHolder(0);
    private final SpringAnimation fillSpring;
    private final FloatValueHolder ring = new FloatValueHolder(0);
    private final SpringAnimation ringSpring;

    private AnimatedEmojiDrawable animatedReaction;
    private long animatedReactionId;
    private ImageReceiver reactionImage;
    private String reactionEmojicon;
    private Drawable reactionDrawable;

    private ColorFilter iconFilter;
    private int iconFilterColor;

    private Paint framePaint;
    private boolean frameGradient;
    private boolean frameDark;

    private boolean looped;
    private boolean reversed;
    private int selected;
    private int slot;
    private boolean armed;
    private boolean stepped;
    private boolean scrubbing;
    private boolean beyondMax;
    private int direction;
    private float anchorY;
    private float lastX;
    private float lastY;
    private float driftX;
    private float driftY;

    public SwipeActionsHelper(View parent, Theme.ResourcesProvider resourcesProvider) {
        this.parent = parent;
        this.resourcesProvider = resourcesProvider;

        positionSpring = new SpringAnimation(position)
                .setSpring(new SpringForce(0).setStiffness(430f).setDampingRatio(0.55f))
                .addUpdateListener((animation, value, velocity) -> invalidate());
        visibilitySpring = new SpringAnimation(visibility)
                .setMinValue(0)
                .setMaxValue(SPRING_SCALE)
                .setSpring(new SpringForce(0).setStiffness(1500f).setDampingRatio(1f))
                .addUpdateListener((animation, value, velocity) -> invalidate());
        fillSpring = new SpringAnimation(fill)
                .setMinValue(0)
                .setSpring(new SpringForce(0).setStiffness(400f).setDampingRatio(0.5f))
                .addUpdateListener((animation, value, velocity) -> invalidate());
        ringSpring = new SpringAnimation(ring)
                .setMinValue(0)
                .setSpring(new SpringForce(0).setStiffness(200f).setDampingRatio(1f))
                .addUpdateListener((animation, value, velocity) -> invalidate());

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
        outlinePaint.setStrokeWidth(AndroidUtilities.dp(2));
        outlineDarkenPaint.setStyle(Paint.Style.STROKE);
        outlineDarkenPaint.setStrokeCap(Paint.Cap.ROUND);
        outlineDarkenPaint.setStrokeWidth(AndroidUtilities.dp(2));
    }

    private void invalidate() {
        parent.invalidate();
    }

    public void setReaction(int account, String emoticon, long documentId) {
        if (documentId != 0) {
            if (animatedReactionId != documentId) {
                detach();
                animatedReaction = AnimatedEmojiDrawable.make(account, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, documentId);
                animatedReaction.addView(parent);
                animatedReactionId = documentId;
            }
            reactionDrawable = animatedReaction;
            return;
        }
        TLRPC.TL_availableReaction reaction = emoticon != null ? MediaDataController.getInstance(account).getReactionsMap().get(emoticon) : null;
        if (reaction != null && reaction.center_icon != null) {
            if (reactionImage == null || !TextUtils.equals(reactionEmojicon, emoticon)) {
                detach();
                reactionImage = new ImageReceiver(parent);
                reactionImage.ignoreNotifications = true;
                reactionImage.onAttachedToWindow();
                reactionImage.setImage(ImageLocation.getForDocument(reaction.center_icon), "40_40_lastreactframe", DocumentObject.getSvgThumb(reaction.static_icon, Theme.key_windowBackgroundGray, 1.0f), "webp", reaction, 1);
                reactionEmojicon = emoticon;
            }
            return;
        }
        detach();
        reactionDrawable = emoticon != null ? Emoji.getEmojiDrawable(emoticon) : null;
    }

    public void detach() {
        if (animatedReaction != null) {
            animatedReaction.removeView(parent);
            animatedReaction = null;
            animatedReactionId = 0;
        }
        if (reactionImage != null) {
            reactionImage.onDetachedFromWindow();
            reactionImage = null;
            reactionEmojicon = null;
        }
        reactionDrawable = null;
    }

    public void start(List<SwipeAction> enabledActions) {
        actions.clear();
        actions.addAll(enabledActions);
        looped = ExteraConfig.getSwipeActionsLoop();
        reversed = ExteraConfig.getSwipeActionsReversed();
        selected = 0;
        slot = 0;
        armed = false;
        stepped = false;
        scrubbing = false;
        liveAlpha.set(0, true);
        anchorY = 0;
        driftX = 0;
        driftY = 0;
        direction = 0;
        reset(positionSpring, position);
        reset(visibilitySpring, visibility);
        reset(fillSpring, fill);
        reset(ringSpring, ring);
        beyondMax = false;
    }

    private static void reset(SpringAnimation spring, FloatValueHolder holder) {
        spring.cancel();
        spring.getSpring().setFinalPosition(0);
        holder.setValue(0);
    }

    public int size() {
        return actions.size();
    }

    public SwipeAction getSelected() {
        if (selected < 0 || selected >= actions.size()) {
            return null;
        }
        return actions.get(selected);
    }

    public void update(float translationX, float x, float y) {
        if (actions.size() < 2) {
            return;
        }
        if (Math.abs(translationX) < AndroidUtilities.dp(50)) {
            armed = false;
            scrubbing = false;
            return;
        }
        if (!armed) {
            armed = true;
            lastX = x;
            lastY = y;
            driftX = 0;
            driftY = 0;
            return;
        }
        float dx = Math.abs(x - lastX);
        float dy = y - lastY;
        lastX = x;
        lastY = y;
        if (!scrubbing) {
            driftX = driftX * 0.7f + dx * 0.3f;
            driftY = driftY * 0.7f + Math.abs(dy) * 0.3f;
            if (driftY < AndroidUtilities.dp(0.5f) || driftY <= driftX * 1.5f) {
                return;
            }
            scrubbing = true;
            anchorY = y;
            return;
        }
        float delta = (y - anchorY) * (direction < 0 ? -1 : 1) * (reversed ? -1 : 1);
        float step = AndroidUtilities.dp(stepped ? 36 : 44);
        if (delta >= step) {
            anchorY = y;
            stepped = true;
            if (looped || selected < actions.size() - 1) {
                move(1);
            }
        } else if (delta <= -step) {
            anchorY = y;
            stepped = true;
            if (looped || selected > 0) {
                move(-1);
            }
        }
    }

    private int chooseDirection(float y) {
        float needed = AndroidUtilities.dp(44) + (actions.size() - 2) * AndroidUtilities.dp(36);
        float below = parent.getHeight() - y;
        return below >= needed || below >= y ? 1 : -1;
    }

    public void select(int index) {
        scrubbing = true;
        if (index == selected || index < 0 || index >= actions.size()) {
            return;
        }
        int delta = index - selected;
        if (looped) {
            delta = Math.floorMod(delta, actions.size());
            if (delta > actions.size() / 2) {
                delta -= actions.size();
            }
        }
        selected = index;
        slot += delta;
        positionSpring.getSpring().setFinalPosition(slot * SPRING_SCALE);
        positionSpring.start();
        invalidate();
    }

    public boolean remove(SwipeAction action) {
        if (scrubbing || actions.size() < 2) {
            return false;
        }
        int index = actions.indexOf(action);
        if (index < 0 || index == selected && visibility.getValue() != 0) {
            return false;
        }
        actions.remove(index);
        if (selected >= actions.size()) {
            selected = actions.size() - 1;
            slot = selected;
            positionSpring.cancel();
            positionSpring.getSpring().setFinalPosition(selected * SPRING_SCALE);
            position.setValue(selected * SPRING_SCALE);
        }
        invalidate();
        return true;
    }

    private void move(int delta) {
        slot += delta;
        selected = Math.floorMod(slot, actions.size());
        try {
            parent.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {
        }
        positionSpring.getSpring().setFinalPosition(slot * SPRING_SCALE);
        positionSpring.start();
        invalidate();
    }

    public void draw(Canvas canvas, float translationX, boolean half, float centerY, float minX) {
        if (actions.isEmpty() || Thread.currentThread() != Looper.getMainLooper().getThread()) {
            return;
        }
        if (direction == 0) {
            direction = chooseDirection(centerY);
        }
        Paint servicePaint = Theme.getThemePaint(Theme.key_paint_chatActionBackground, resourcesProvider);
        Paint darkenPaint = Theme.chat_actionBackgroundGradientDarkenPaint;
        if (outlinePaint.getColor() != servicePaint.getColor()) {
            outlinePaint.setColor(servicePaint.getColor());
        }
        if (outlineDarkenPaint.getColor() != darkenPaint.getColor()) {
            outlineDarkenPaint.setColor(darkenPaint.getColor());
        }
        if (outlinePaint.getShader() != servicePaint.getShader()) {
            outlinePaint.setShader(servicePaint.getShader());
        }
        if (outlineDarkenPaint.getShader() != darkenPaint.getShader()) {
            outlineDarkenPaint.setShader(darkenPaint.getShader());
        }
        framePaint = servicePaint;
        frameGradient = resourcesProvider != null && resourcesProvider.hasGradientService();
        frameDark = ColorUtils.calculateLuminance(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)) <= 0.5d;
        int darkenColor = outlineDarkenPaint.getColor();

        float fillProgress = fill.getValue() / SPRING_SCALE;
        if (fillProgress > 1f) {
            beyondMax = true;
        }
        if (visibility.getValue() == 0) {
            reset(fillSpring, fill);
            reset(ringSpring, ring);
            beyondMax = false;
        }
        boolean filled = fillSpring.getSpring().getFinalPosition() == SPRING_SCALE;
        float progress = filled ? 1f : MathUtils.clamp((-translationX - AndroidUtilities.dp(20)) / AndroidUtilities.dp(30), 0f, 1f);
        if (progress == 1f && !filled) {
            fillSpring.getSpring().setFinalPosition(SPRING_SCALE);
            fillSpring.start();
            ringSpring.getSpring().setFinalPosition(SPRING_SCALE);
            ringSpring.start();
        }
        float visibilityTarget = translationX <= -AndroidUtilities.dp(20) ? SPRING_SCALE : 0f;
        if (visibilityTarget != visibilitySpring.getSpring().getFinalPosition()) {
            visibilitySpring.getSpring().setFinalPosition(visibilityTarget);
            if (!visibilitySpring.isRunning()) {
                visibilitySpring.start();
            }
        }
        float visibilityProgress = visibility.getValue() / SPRING_SCALE;
        float width = parent.getMeasuredWidth();
        float cx = Math.max(translationX * (half ? 0.5f : 1f) + width, Math.min((minX + width) / 2f, width));
        float outerScale = beyondMax ? fillProgress : visibilityProgress;
        float holeScale = beyondMax ? 0f : 1f - fillProgress;
        float currentPosition = position.getValue() / SPRING_SCALE;
        float spacing = AndroidUtilities.dp(36) * direction;
        float live = liveAlpha.set(scrubbing);

        int count = actions.size();
        int from = (int) Math.ceil(currentPosition - 2f);
        int to = (int) Math.floor(currentPosition + 2f);
        if (!looped || count < 2) {
            from = Math.max(from, 0);
            to = Math.min(to, count - 1);
        }
        for (int i = from; i <= to; i++) {
            if (i == slot) {
                continue;
            }
            float offset = i - currentPosition;
            drawBubble(canvas, cx, centerY + offset * spacing, outerScale, holeScale, visibilityProgress, progress, Math.abs(offset), live, i, false);
        }
        float offset = slot - currentPosition;
        drawBubble(canvas, cx, centerY + offset * spacing, outerScale, holeScale, visibilityProgress, progress, Math.abs(offset), 1f, slot, true);
        outlineDarkenPaint.setColor(darkenColor);
        darkenPaint.setColor(darkenColor);
    }

    private void drawBubble(Canvas canvas, float cx, float cy, float outerScale, float holeScale, float visibilityProgress, float progress, float distance, float live, int index, boolean current) {
        if (distance >= 2f) {
            return;
        }
        float near = Math.max(0f, 1f - distance);
        float distanceAlpha = Math.max(near, (2f - distance) * 0.45f);
        float scale = (near * 0.3f + 0.7f) * (CubicBezierInterpolator.EASE_OUT_BACK.getInterpolation(live) * 0.15f + 0.85f);
        float bubbleScale = outerScale * scale;
        float hole = holeScale * scale;
        float alpha = visibilityProgress * distanceAlpha * live;
        if (alpha <= 0) {
            return;
        }
        Paint paint = framePaint;
        Paint darkenPaint = Theme.chat_actionBackgroundGradientDarkenPaint;
        boolean gradient = frameGradient;
        boolean dark = frameDark;
        float baseRadius = AndroidUtilities.dp(16);

        if (current && progress > 0 && fill.getValue() == 0) {
            setRect(cx, cy, baseRadius * bubbleScale - outlinePaint.getStrokeWidth() / 2f);
            applyServiceShaderMatrix();
            float sweep = 360f * progress;
            int outlineAlpha = outlinePaint.getAlpha();
            outlinePaint.setAlpha((int) (outlineAlpha * alpha));
            canvas.drawArc(rect, -90f, sweep, false, outlinePaint);
            outlinePaint.setAlpha(outlineAlpha);
            if (gradient) {
                int darkenAlpha = outlineDarkenPaint.getAlpha();
                if (dark) {
                    outlineDarkenPaint.setColor(0xffffffff);
                }
                outlineDarkenPaint.setAlpha((int) (darkenAlpha * alpha));
                canvas.drawArc(rect, -90f, sweep, false, outlineDarkenPaint);
            }
        }

        float radius = baseRadius * bubbleScale;
        drawServiceCircle(canvas, cx, cy, radius, paint, darkenPaint, 0.6f * alpha * progress, gradient, dark);

        if (hole != 0) {
            setRect(cx, cy, baseRadius * hole);
            path.rewind();
            path.addRoundRect(rect, baseRadius, baseRadius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(path, Region.Op.DIFFERENCE);
        }
        drawServiceCircle(canvas, cx, cy, radius, paint, darkenPaint, 0.4f * alpha, gradient, dark);
        if (hole != 0) {
            canvas.restore();
        }

        float ringProgress = ring.getValue() / SPRING_SCALE;
        if (current && ringProgress != 0 && ringProgress != 1f) {
            float strokeWidth = outlinePaint.getStrokeWidth();
            float ringStroke = (1f - ringProgress) * strokeWidth;
            if (ringStroke != 0) {
                float ringRadius = baseRadius * (ringProgress + 1f);
                setRect(cx, cy, ringRadius - ringStroke);
                applyServiceShaderMatrix();
                int outlineAlpha = outlinePaint.getAlpha();
                outlinePaint.setAlpha((int) (outlineAlpha * alpha));
                outlinePaint.setStrokeWidth(ringStroke);
                canvas.drawRoundRect(rect, ringRadius, ringRadius, outlinePaint);
                outlinePaint.setStrokeWidth(strokeWidth);
                outlinePaint.setAlpha(outlineAlpha);
                if (gradient) {
                    int darkenAlpha = outlineDarkenPaint.getAlpha();
                    if (dark) {
                        outlineDarkenPaint.setColor(0xffffffff);
                    }
                    outlineDarkenPaint.setAlpha((int) (darkenAlpha * alpha));
                    outlineDarkenPaint.setStrokeWidth(ringStroke);
                    canvas.drawRoundRect(rect, ringRadius, ringRadius, outlineDarkenPaint);
                    outlineDarkenPaint.setStrokeWidth(strokeWidth);
                }
            }
        }

        SwipeAction action = actions.get(Math.floorMod(index, actions.size()));
        // TODO(openextera): decompile failed (duplicated blocks with inverted conditions), verify reaction/reply icon conditions
        if (action == SwipeAction.REACTION && reactionImage != null) {
            float size = AndroidUtilities.dp(20) / 2f * bubbleScale;
            reactionImage.setImageCoords(cx - size, cy - size, size * 2f, size * 2f);
            reactionImage.setAlpha(alpha);
            reactionImage.draw(canvas);
            return;
        }
        if (action == SwipeAction.REACTION && reactionDrawable != null) {
            float size = AndroidUtilities.dp(20) / 2f * bubbleScale;
            setRect(cx, cy, size);
            path.rewind();
            path.addRoundRect(rect, size * 0.35f, size * 0.35f, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(path);
            reactionDrawable.setAlpha((int) (alpha * 255));
            reactionDrawable.setBounds((int) (cx - size), (int) (cy - size), (int) (cx + size), (int) (cy + size));
            reactionDrawable.draw(canvas);
            reactionDrawable.setAlpha(255);
            canvas.restore();
            return;
        }

        boolean replyIcon = actions.size() == 1 && action == SwipeAction.REPLY;
        Drawable icon;
        if (replyIcon) {
            icon = Theme.getThemeDrawable(Theme.key_drawable_replyIcon, resourcesProvider);
        } else {
            icon = getIcon(action, (Math.floorDiv(index, actions.size()) & 1) != 0);
        }
        if (icon == null) {
            return;
        }
        float iconScale;
        if (replyIcon) {
            iconScale = bubbleScale;
        } else {
            Float fit = iconFits.get(action.iconRes);
            iconScale = bubbleScale * action.iconTrim * (fit != null ? fit : 1f);
        }
        float halfWidth = icon.getIntrinsicWidth() / 2f * iconScale;
        float halfHeight = icon.getIntrinsicHeight() / 2f * iconScale;
        icon.setColorFilter(getIconFilter());
        icon.setAlpha((int) (alpha * 255));
        icon.setBounds((int) (cx - halfWidth), (int) (cy - halfHeight), (int) (cx + halfWidth), (int) (cy + halfHeight));
        icon.draw(canvas);
    }

    private void drawServiceCircle(Canvas canvas, float cx, float cy, float radius, Paint paint, Paint darkenPaint, float alpha, boolean gradient, boolean dark) {
        setRect(cx, cy, radius);
        applyServiceShaderMatrix();
        path.rewind();
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        int paintAlpha = paint.getAlpha();
        paint.setAlpha((int) (paintAlpha * alpha));
        canvas.drawPath(path, paint);
        paint.setAlpha(paintAlpha);
        if (gradient) {
            int darkenAlpha = darkenPaint.getAlpha();
            if (dark) {
                darkenPaint.setColor(0xffffffff);
            }
            darkenPaint.setAlpha((int) (alpha * darkenAlpha));
            canvas.drawPath(path, darkenPaint);
            darkenPaint.setAlpha(darkenAlpha);
        }
    }

    private void setRect(float cx, float cy, float radius) {
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
    }

    private void applyServiceShaderMatrix() {
        int width = parent.getMeasuredWidth();
        float y = parent.getY() + rect.top;
        if (resourcesProvider != null) {
            resourcesProvider.applyServiceShaderMatrix(width, AndroidUtilities.displaySize.y, 0, y);
        } else {
            Theme.applyServiceShaderMatrix(width, AndroidUtilities.displaySize.y, 0, y);
        }
    }

    private ColorFilter getIconFilter() {
        int color = Theme.getColor(Theme.key_chat_serviceIcon, resourcesProvider);
        if (iconFilter == null || iconFilterColor != color) {
            iconFilterColor = color;
            iconFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
        }
        return iconFilter;
    }

    private float measureIconFit(Drawable drawable) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            return 1f;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(new Canvas(bitmap));
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        bitmap.recycle();
        int left = width, top = height, right = -1, bottom = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((pixels[y * width + x] >>> 24) >= 8) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (right < left || bottom < top) {
            return 1f;
        }
        return Math.min(AndroidUtilities.dpf2(15f) / (right - left + 1), AndroidUtilities.dpf2(16.5f) / (bottom - top + 1));
    }

    private Drawable getIcon(SwipeAction action, boolean spare) {
        SparseArray<Drawable> cache = spare ? spareIcons : icons;
        Drawable cached = cache.get(action.iconRes);
        if (cached != null) {
            return cached;
        }
        Drawable drawable = parent.getContext().getDrawable(action.iconRes);
        if (drawable == null) {
            return null;
        }
        if (iconFits.get(action.iconRes) == null) {
            iconFits.put(action.iconRes, measureIconFit(drawable));
        }
        drawable = drawable.mutate();
        cache.put(action.iconRes, drawable);
        return drawable;
    }
}
