package com.exteragram.messenger.icons.ui.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.SparseIntArray;
import android.view.View;

import com.exteragram.messenger.icons.ExteraResources;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.icons.IconPack;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class IconPackPreviewView extends View {

    private static final int[] DEFAULT_ICONS = {R.drawable.msg_sticker, R.drawable.msg_link2, R.drawable.msg_pin, R.drawable.msg_photos};
    private static final int MAX_ICONS = 20;

    private final List<Integer> availableIconIds = new ArrayList<>();
    private final Drawable[] currentIcons = new Drawable[MAX_ICONS];
    private final Drawable[] nextIcons = new Drawable[MAX_ICONS];
    private final int[] currentIconIds = new int[MAX_ICONS];
    private final Random random = new Random();

    private final int rows = 2;
    private final int cols = 2;
    private int count = 4;
    private final int iconSize = AndroidUtilities.dp(20);
    private final int gap = AndroidUtilities.dp(4);

    private boolean isCircularMode = false;
    private final int circInnerRadius = AndroidUtilities.dp(46);
    private final int circInnerIconSize = AndroidUtilities.dp(24);
    private final int circOuterRadius = AndroidUtilities.dp(72);
    private final int circOuterIconSize = AndroidUtilities.dp(20);
    private final int circCenterSize = AndroidUtilities.dp(36);

    private IconPack currentPack;
    private float animationProgress = 0f;
    private ValueAnimator animator;
    private boolean attached;
    private boolean shouldAnimate;
    private int refreshTimeMilliseconds = 5000;

    private final Runnable changeRunnable = this::animateToNext;

    public IconPackPreviewView(Context context) {
        super(context);
    }

    public void setCircularMode(boolean circular) {
        isCircularMode = circular;
        if (circular) {
            count = 13;
        }
        requestLayout();
        if (currentPack != null) {
            setIconPack(currentPack);
        }
    }

    public void setRefreshTime(int milliseconds) {
        refreshTimeMilliseconds = milliseconds;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isCircularMode) {
            int height = (circOuterRadius + circOuterIconSize / 2) * 2 + AndroidUtilities.dp(8);
            setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), resolveSize(height, heightMeasureSpec));
        } else {
            setMeasuredDimension(
                resolveSize(iconSize * cols + gap + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
                resolveSize(iconSize * rows + gap + getPaddingTop() + getPaddingBottom(), heightMeasureSpec)
            );
        }
    }

    public void setIconPack(IconPack iconPack) {
        if (currentPack != null && sameIconSource(currentPack, iconPack) && !availableIconIds.isEmpty() && availableIconIds.size() >= count) {
            return;
        }
        currentPack = iconPack;
        availableIconIds.clear();
        if (iconPack.isBase()) {
            for (int id : DEFAULT_ICONS) {
                availableIconIds.add(id);
            }
            shouldAnimate = false;
        } else {
            ArrayList<String> names = new ArrayList<>();
            HashSet<String> uniqueFiles = new HashSet<>();
            for (Map.Entry<String, String> entry : iconPack.getIcons().entrySet()) {
                if (uniqueFiles.add(entry.getValue())) {
                    names.add(entry.getKey());
                }
            }
            if (names.isEmpty()) {
                availableIconIds.add(R.drawable.msg_media);
            } else {
                ConcurrentHashMap<String, Integer> systemIcons = IconManager.INSTANCE.getSystemIcons();
                if (!systemIcons.isEmpty()) {
                    for (String name : names) {
                        Integer id = systemIcons.get(name);
                        if (id != null) {
                            availableIconIds.add(id);
                        }
                    }
                } else {
                    int checked = 0;
                    for (String name : names) {
                        int id = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
                        if (id != 0) {
                            availableIconIds.add(id);
                        }
                        if (++checked > 30) {
                            break;
                        }
                    }
                }
            }
            shouldAnimate = uniqueFiles.size() >= count * 2;
        }
        loadIcons(false);
    }

    private static boolean sameIconSource(IconPack a, IconPack b) {
        if (!a.getId().equals(b.getId())) {
            return false;
        }
        Map<String, String> iconsA = a.getIcons();
        Map<String, String> iconsB = b.getIcons();
        if (iconsA != iconsB) {
            return iconsA.isEmpty() && iconsB.isEmpty();
        }
        return true;
    }

    private void loadIcons(boolean next) {
        final IconPack iconPack = currentPack;
        final ArrayList<Integer> ids = new ArrayList<>(availableIconIds);
        final int[] excludeIds = next ? currentIconIds.clone() : null;
        final int needed = iconPack.isBase() ? count : Math.min(count, ids.size());
        Utilities.globalQueue.postRunnable(() -> {
            List<Integer> picked = pickRandomIconIdsInternal(ids, needed, excludeIds);
            Drawable[] drawables = new Drawable[currentIconIds.length];
            int[] pickedIds = new int[currentIconIds.length];
            for (int i = 0; i < currentIconIds.length; i++) {
                if (i < picked.size()) {
                    pickedIds[i] = picked.get(i);
                    drawables[i] = getIconDrawableInternal(iconPack, pickedIds[i]);
                } else if (!picked.isEmpty() && isCircularMode) {
                    pickedIds[i] = picked.get(i % picked.size());
                    drawables[i] = getIconDrawableInternal(iconPack, pickedIds[i]);
                } else {
                    pickedIds[i] = 0;
                    drawables[i] = null;
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (currentPack != iconPack) {
                    return;
                }
                if (next) {
                    System.arraycopy(drawables, 0, nextIcons, 0, nextIcons.length);
                    startTransition(picked);
                    return;
                }
                for (int i = 0; i < currentIcons.length; i++) {
                    currentIconIds[i] = pickedIds[i];
                    currentIcons[i] = drawables[i];
                }
                invalidate();
                if (attached && shouldAnimate) {
                    scheduleNext();
                } else {
                    removeCallbacks(changeRunnable);
                }
            });
        });
    }

    private List<Integer> pickRandomIconIdsInternal(List<Integer> source, int needed, int[] excludeIds) {
        ArrayList<Integer> result = new ArrayList<>();
        if (source.isEmpty()) {
            return result;
        }
        ArrayList<Integer> candidates = new ArrayList<>(source);
        if (excludeIds != null) {
            for (int id : excludeIds) {
                candidates.remove(Integer.valueOf(id));
            }
        }
        if (candidates.isEmpty()) {
            candidates.addAll(source);
        }
        HashSet<Integer> indices = new HashSet<>();
        int size = candidates.size();
        for (int attempt = 0; indices.size() < needed && indices.size() < size && attempt < needed * 4; attempt++) {
            indices.add(random.nextInt(size));
        }
        if (indices.size() < needed && indices.size() < size) {
            for (int i = 0; i < size && indices.size() < needed; i++) {
                indices.add(i);
            }
        }
        for (Integer index : indices) {
            result.add(candidates.get(index));
        }
        return result;
    }

    private Drawable getIconDrawableInternal(IconPack iconPack, int resId) {
        if (resId == 0) {
            return null;
        }
        Drawable drawable = null;
        if (iconPack.isBase()) {
            SparseIntArray preinstalledMap = iconPack.getPreinstalledMap();
            int mappedId = preinstalledMap != null ? preinstalledMap.get(resId, -1) : -1;
            if (mappedId != -1) {
                drawable = getResources().getDrawable(mappedId);
            }
        } else {
            drawable = IconManager.INSTANCE.getPackIconDrawable(iconPack, resId);
        }
        if (drawable == null) {
            if (getResources() instanceof ExteraResources) {
                try {
                    drawable = ((ExteraResources) getResources()).getOriginalDrawable(resId);
                } catch (Exception e) {
                    drawable = getResources().getDrawable(resId);
                }
            } else {
                drawable = getResources().getDrawable(resId);
            }
        }
        if (drawable == null) {
            return null;
        }
        drawable = drawable.mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        return drawable;
    }

    private void scheduleNext() {
        removeCallbacks(changeRunnable);
        if (attached && shouldAnimate) {
            postDelayed(changeRunnable, refreshTimeMilliseconds);
        }
    }

    private void animateToNext() {
        if (attached && shouldAnimate) {
            loadIcons(true);
        }
    }

    private void startTransition(List<Integer> nextIds) {
        if (!attached) {
            return;
        }
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(300);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (animator == animation) {
                    animator = null;
                }
                if (cancelled || !attached) {
                    return;
                }
                for (int i = 0; i < currentIcons.length; i++) {
                    currentIconIds[i] = i < nextIds.size() ? nextIds.get(i) : 0;
                    currentIcons[i] = nextIcons[i];
                    nextIcons[i] = null;
                }
                animationProgress = 0f;
                invalidate();
                scheduleNext();
            }
        });
        animator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (shouldAnimate) {
            scheduleNext();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        removeCallbacks(changeRunnable);
        if (animator != null) {
            ValueAnimator a = animator;
            animator = null;
            a.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isCircularMode) {
            drawCircularLayout(canvas);
            return;
        }
        if (count == 4) {
            int visible = 0;
            for (Drawable drawable : currentIcons) {
                if (drawable != null) {
                    visible++;
                }
            }
            if (visible == 1) {
                int size = AndroidUtilities.dp(24);
                drawIcon(canvas, 0, (getWidth() - size) / 2, (getHeight() - size) / 2, size, 1f);
            } else if (visible == 2) {
                int size = AndroidUtilities.dp(20);
                int spacing = AndroidUtilities.dp(4);
                int top = (getHeight() - size) / 2;
                drawIcon(canvas, 0, 0, top, size, 1f);
                drawIcon(canvas, 1, size + spacing, top, size, 1f);
            } else if (visible == 3) {
                int size = AndroidUtilities.dp(20);
                int spacing = AndroidUtilities.dp(4);
                int step = size + spacing;
                drawIcon(canvas, 0, 0, 0, size, 1f);
                drawIcon(canvas, 1, step, 0, size, 1f);
                drawIcon(canvas, 2, (getWidth() - size) / 2, step, size, 1f);
            } else {
                int size = AndroidUtilities.dp(20);
                int step = size + AndroidUtilities.dp(4);
                drawIcon(canvas, 0, 0, 0, size, 1f);
                drawIcon(canvas, 1, step, 0, size, 1f);
                drawIcon(canvas, 2, 0, step, size, 1f);
                drawIcon(canvas, 3, step, step, size, 1f);
            }
            return;
        }
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int index = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols && index < currentIcons.length; col++) {
                drawIcon(canvas, index, left + (iconSize + gap) * col, top + (iconSize + gap) * row, iconSize, 1f);
                index++;
            }
        }
    }

    private void drawCircularLayout(Canvas canvas) {
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        drawIcon(canvas, 0, cx - circCenterSize / 2, cy - circCenterSize / 2, circCenterSize, 1f);
        for (int i = 0; i < 6; i++) {
            int index = 1 + i;
            if (index >= currentIcons.length) {
                break;
            }
            double angle = Math.toRadians(i * 60.0);
            int x = (int) (cx + circInnerRadius * Math.cos(angle)) - circInnerIconSize / 2;
            int y = (int) (cy + circInnerRadius * Math.sin(angle)) - circInnerIconSize / 2;
            drawIcon(canvas, index, x, y, circInnerIconSize, 0.7f);
        }
        for (int i = 0; i < 6; i++) {
            int index = 7 + i;
            if (index >= currentIcons.length) {
                return;
            }
            double angle = Math.toRadians(i * 60.0 + 30.0);
            int x = (int) (cx + circOuterRadius * Math.cos(angle)) - circOuterIconSize / 2;
            int y = (int) (cy + circOuterRadius * Math.sin(angle)) - circOuterIconSize / 2;
            drawIcon(canvas, index, x, y, circOuterIconSize, 0.3f);
        }
    }

    private void drawIcon(Canvas canvas, int index, int x, int y, int size, float alpha) {
        if (index >= currentIcons.length) {
            return;
        }
        canvas.save();
        canvas.translate(x, y);
        Drawable current = currentIcons[index];
        Drawable next = nextIcons[index];
        if (current != null) {
            current.setBounds(0, 0, size, size);
            current.setAlpha((int) ((1f - animationProgress) * alpha * 255));
            current.draw(canvas);
        }
        if (next != null) {
            next.setBounds(0, 0, size, size);
            next.setAlpha((int) (animationProgress * alpha * 255));
            next.draw(canvas);
        }
        canvas.restore();
    }
}
