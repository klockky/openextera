package com.exteragram.messenger.icons.ui.picker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.icons.IconPack;
import com.exteragram.messenger.icons.ui.IconPacksEditorActivity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

public class IconPickerView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private static final int search_button = 0;
    private static final int other_button = 1;
    private static final int all_icons_button = 2;
    private static final int save_and_exit_button = 3;

    private static final float FAB_EDGE_RIGHT = Integer.MAX_VALUE;
    private static final float FAB_EDGE_LEFT = -Integer.MAX_VALUE;
    private static final int DIM_STATUS_BAR_COLOR = 0x7A000000;

    private final ActionBar actionBar;
    private final ImageView actionIcon;
    private final LinearLayout bigLayout;
    private SpringAnimation fabXSpring;
    private SpringAnimation fabYSpring;
    private Drawable floatingButtonBackground;
    private final FrameLayout floatingButtonContainer;
    private boolean inLongPress;
    private boolean isBigMenuShown;
    private boolean isFromFling;
    private boolean isScrollDisallowed;
    private boolean isScrolling;
    private final UniversalRecyclerView listView;
    private final SharedPreferences mPrefs;
    private final Runnable onLongPress;
    private final ActionBarMenuItem otherButton;
    private String query;
    private final ActionBarMenuSubItem saveItem;
    private boolean searching;
    private int systemBottomInset = 0;
    private int systemTopInset = 0;
    private final int touchSlop;
    private int wasStatusBar;

    public IconPickerView(Context context) {
        super(context);
        onLongPress = () -> {
            inLongPress = true;
            try {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            } catch (Exception ignore) {
            }
        };
        mPrefs = context.getSharedPreferences("icon_picker", Context.MODE_PRIVATE);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        final GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            private float startX;
            private float startY;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (inLongPress || isBigMenuShown) {
                    return false;
                }
                showIconList(true);
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (!isScrolling || inLongPress) {
                    return false;
                }
                float finalX = fabXSpring.getSpring().getFinalPosition() + velocityX / 7f;
                fabXSpring.getSpring().setFinalPosition(clampX(getResources().getDisplayMetrics(), finalX >= getWidth() / 2f ? FAB_EDGE_RIGHT : FAB_EDGE_LEFT));
                fabYSpring.getSpring().setFinalPosition(clampY(getResources().getDisplayMetrics(), fabYSpring.getSpring().getFinalPosition() + velocityY / 10f));
                fabXSpring.start();
                fabYSpring.start();
                isFromFling = true;
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (!inLongPress) {
                    AndroidUtilities.cancelRunOnUIThread(onLongPress);
                }
                if (!isScrolling && !isScrollDisallowed) {
                    if (Math.abs(distanceX) >= touchSlop || Math.abs(distanceY) >= touchSlop) {
                        startX = fabXSpring.getSpring().getFinalPosition();
                        startY = fabYSpring.getSpring().getFinalPosition();
                        isScrolling = true;
                    } else {
                        isScrollDisallowed = false;
                    }
                }
                if (isScrolling && !inLongPress) {
                    fabXSpring.getSpring().setFinalPosition(startX + e2.getRawX() - e1.getRawX());
                    fabYSpring.getSpring().setFinalPosition(startY + e2.getRawY() - e1.getRawY());
                    fabXSpring.start();
                    fabYSpring.start();
                }
                return isScrolling;
            }
        });
        gestureDetector.setIsLongpressEnabled(false);

        floatingButtonContainer = new FrameLayout(context) {
            @Override
            public void invalidate() {
                super.invalidate();
                IconPickerView.this.invalidate();
            }

            @Override
            public void setTranslationX(float translationX) {
                super.setTranslationX(translationX);
                IconPickerView.this.invalidate();
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                IconPickerView.this.invalidate();
            }

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                boolean result = gestureDetector.onTouchEvent(event);
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    AndroidUtilities.runOnUIThread(onLongPress, 200);
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    AndroidUtilities.cancelRunOnUIThread(onLongPress);
                    if (!isFromFling) {
                        updateSpringPositions();
                    }
                    inLongPress = false;
                    isScrolling = false;
                    isScrollDisallowed = false;
                    isFromFling = false;
                }
                return result;
            }
        };
        actionIcon = new ImageView(context);
        actionIcon.setImageResource(R.drawable.msg_palette);
        floatingButtonContainer.addView(actionIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        floatingButtonContainer.setVisibility(GONE);
        addView(floatingButtonContainer, LayoutHelper.createFrame(56, 56));

        bigLayout = new LinearLayout(context);
        bigLayout.setOrientation(LinearLayout.VERTICAL);
        bigLayout.setVisibility(GONE);

        actionBar = new ActionBar(context);
        actionBar.setOccupyStatusBar(false);
        updateTitle();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(search_button, R.drawable.outline_header_search)
            .setIsSearchField(true)
            .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    searching = true;
                    if (otherButton != null) {
                        otherButton.setVisibility(GONE);
                    }
                }

                @Override
                public void onSearchCollapse() {
                    searching = false;
                    query = null;
                    if (otherButton != null) {
                        otherButton.setVisibility(VISIBLE);
                    }
                    listView.adapter.update(true);
                }

                @Override
                public void onTextChanged(EditText editText) {
                    query = editText.getText().toString();
                    listView.adapter.update(true);
                }
            });
        otherButton = menu.addItem(other_button, R.drawable.ic_ab_other);
        otherButton.addSubItem(all_icons_button, R.drawable.msg_media, LocaleController.getString(R.string.IconPickerAllIcons));
        saveItem = otherButton.addSubItem(save_and_exit_button, R.drawable.ic_ab_done, LocaleController.getString(R.string.IconPickerSaveAndExit));
        int saveColor = Theme.getColor(Theme.key_featuredStickers_addButtonPressed);
        saveItem.setColors(saveColor, saveColor);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    showIconList(false);
                } else if (id == save_and_exit_button) {
                    ExteraConfig.setEditingIconPackId(null);
                    if (context instanceof LaunchActivity) {
                        IconPickerController.setActive((LaunchActivity) context, false);
                    }
                } else if (id == all_icons_button && ExteraConfig.getEditingIconPackId() != null) {
                    IconPack iconPack = IconManager.INSTANCE.findPackById(ExteraConfig.getEditingIconPackId());
                    if (iconPack != null && context instanceof LaunchActivity) {
                        ((LaunchActivity) context).presentFragment(new IconPacksEditorActivity(iconPack));
                        showIconList(false);
                    }
                }
            }
        });
        bigLayout.addView(actionBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listView = new UniversalRecyclerView(context, UserConfig.selectedAccount, 0, this::fillItems, this::onClick, null, null);
        listView.setLayoutManager(new LinearLayoutManager(context));
        bigLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        addView(bigLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.NO_GRAVITY, 8, 8, 8, 8));
        bigLayout.setClipToOutline(true);
        bigLayout.setOutlineProvider(ViewOutlineProviderImpl.boundsWithRoundRect(AndroidUtilities.dp(10)));

        setOnApplyWindowInsetsListener((v, insets) -> {
            applyInsets(insets);
            if (!isScrolling && !isBigMenuShown) {
                updateSpringPositions();
            }
            return insets;
        });

        updateDrawables();
        setWillNotDraw(false);
    }

    private void applyInsets(WindowInsets insets) {
        systemTopInset = insets.getSystemWindowInsetTop();
        systemBottomInset = insets.getSystemWindowInsetBottom();
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) bigLayout.getLayoutParams();
        if (layoutParams != null) {
            layoutParams.topMargin = AndroidUtilities.dp(8) + systemTopInset;
            layoutParams.bottomMargin = AndroidUtilities.dp(8) + systemBottomInset;
            bigLayout.setLayoutParams(layoutParams);
        }
    }

    private void updateSpringPositions() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        fabXSpring.getSpring().setFinalPosition(clampX(displayMetrics, fabXSpring.getSpring().getFinalPosition() >= getWidth() / 2f ? FAB_EDGE_RIGHT : FAB_EDGE_LEFT));
        fabYSpring.getSpring().setFinalPosition(clampY(displayMetrics, fabYSpring.getSpring().getFinalPosition()));
        fabXSpring.start();
        fabYSpring.start();
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        IconManager.INSTANCE.showReplaceAlert(getContext(), item.id);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final String lowerQuery = searching && !TextUtils.isEmpty(query) ? query.toLowerCase() : null;
        final IconPack iconPack = ExteraConfig.getEditingIconPackId() != null ? IconManager.INSTANCE.findPackById(ExteraConfig.getEditingIconPackId()) : null;
        IconObserver.INSTANCE.getUsedIcons().stream()
            .map(resId -> createIconItem(lowerQuery, iconPack, resId))
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing((UItem item) -> item.text.toString()))
            .forEach(items::add);
    }

    private UItem createIconItem(String lowerQuery, IconPack iconPack, Integer resId) {
        try {
            String name = getResources().getResourceEntryName(resId);
            if (lowerQuery != null && !name.toLowerCase().contains(lowerQuery) || IconManager.INSTANCE.isBlacklisted(name)) {
                return null;
            }
            UItem item = IconPacksEditorActivity.EditorIconCell.Factory.asIcon(resId, name, iconPack);
            item.transparent = true;
            return item;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == bigLayout) {
            canvas.drawColor(Color.argb((int) (bigLayout.getAlpha() * 122), 0, 0, 0));
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public boolean onBackPressed(boolean invoked) {
        if (!isBigMenuShown) {
            return false;
        }
        if (invoked) {
            if (searching) {
                actionBar.closeSearchField();
                return true;
            }
            showIconList(false);
        }
        return true;
    }

    public void saveConfig() {
        mPrefs.edit()
            .putFloat("x", fabXSpring.getSpring().getFinalPosition())
            .putFloat("y", fabYSpring.getSpring().getFinalPosition())
            .apply();
    }

    private void updateTitle() {
        if (actionBar == null || ExteraConfig.getEditingIconPackId() == null) {
            return;
        }
        IconPack iconPack = IconManager.INSTANCE.findPackById(ExteraConfig.getEditingIconPackId());
        if (iconPack != null) {
            actionBar.setTitle(iconPack.getName());
        }
    }

    private void updateDrawables() {
        Drawable circle = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        Drawable shadow = ResourcesCompat.getDrawable(getResources(), R.drawable.floating_shadow, getContext().getTheme()).mutate();
        shadow.setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.MULTIPLY));
        CombinedDrawable combinedDrawable = new CombinedDrawable(shadow, circle, 0, 0);
        combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
        floatingButtonBackground = combinedDrawable;

        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_dialogBackground));
        background.setCornerRadius(AndroidUtilities.dp(10));
        bigLayout.setBackground(background);
        bigLayout.setElevation(AndroidUtilities.dp(4));

        if (actionIcon != null) {
            actionIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.SRC_IN));
        }
        if (actionBar != null) {
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
            actionBar.setTitleColor(Theme.getColor(Theme.key_dialogTextBlack));
            actionBar.setItemsColor(Theme.getColor(Theme.key_dialogTextBlack), false);
            actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_dialogButtonSelector), false);
        }
        if (otherButton != null) {
            otherButton.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), false);
            otherButton.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), true);
            otherButton.redrawPopup(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
            if (saveItem != null) {
                int saveColor = Theme.getColor(Theme.key_featuredStickers_addButtonPressed);
                saveItem.setColors(saveColor, saveColor);
            }
        }
        invalidate();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return isBigMenuShown;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(floatingButtonContainer.getTranslationX(), floatingButtonContainer.getTranslationY());
        canvas.scale(floatingButtonContainer.getScaleX(), floatingButtonContainer.getScaleY(), floatingButtonContainer.getPivotX(), floatingButtonContainer.getPivotY());
        floatingButtonBackground.setAlpha((int) (floatingButtonContainer.getAlpha() * 255));
        floatingButtonBackground.setBounds(floatingButtonContainer.getLeft(), floatingButtonContainer.getTop(), floatingButtonContainer.getRight(), floatingButtonContainer.getBottom());
        floatingButtonBackground.draw(canvas);
        canvas.restore();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.iconPackUpdated) {
            updateTitle();
            Parcelable state = listView.getLayoutManager() != null ? listView.getLayoutManager().onSaveInstanceState() : null;
            listView.adapter.update(true);
            if (state != null) {
                listView.getLayoutManager().onRestoreInstanceState(state);
            }
        } else if (id == NotificationCenter.didSetNewTheme) {
            updateDrawables();
            listView.adapter.update(false);
        }
    }

    private float clampX(DisplayMetrics displayMetrics, float x) {
        return MathUtils.clamp(x, AndroidUtilities.dp(16), displayMetrics.widthPixels - AndroidUtilities.dp(72));
    }

    private float clampY(DisplayMetrics displayMetrics, float y) {
        return MathUtils.clamp(y, systemTopInset + AndroidUtilities.dp(16), displayMetrics.heightPixels - systemBottomInset - AndroidUtilities.dp(72));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        WindowInsets rootInsets = getRootWindowInsets();
        if (rootInsets != null) {
            applyInsets(rootInsets);
        }

        float savedX = mPrefs.getFloat("x", -1f);
        float savedY = mPrefs.getFloat("y", -1f);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        floatingButtonContainer.setTranslationX(clampX(displayMetrics, savedX == -1f || savedX >= displayMetrics.widthPixels / 2f ? FAB_EDGE_RIGHT : FAB_EDGE_LEFT));
        floatingButtonContainer.setTranslationY(clampY(displayMetrics, savedY == -1f ? displayMetrics.heightPixels / 2f : savedY));

        fabXSpring = new SpringAnimation(floatingButtonContainer, DynamicAnimation.TRANSLATION_X, floatingButtonContainer.getTranslationX())
            .setSpring(new SpringForce(floatingButtonContainer.getTranslationX()).setStiffness(650f).setDampingRatio(0.75f));
        fabYSpring = new SpringAnimation(floatingButtonContainer, DynamicAnimation.TRANSLATION_Y, floatingButtonContainer.getTranslationY())
            .setSpring(new SpringForce(floatingButtonContainer.getTranslationY()).setStiffness(650f).setDampingRatio(0.75f));

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.iconPackUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        fabXSpring.cancel();
        fabYSpring.cancel();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.iconPackUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
    }

    public void showIconList(boolean show) {
        if (isBigMenuShown == show) {
            return;
        }
        isBigMenuShown = show;
        if (show) {
            bigLayout.setVisibility(VISIBLE);
            listView.adapter.update(true);
        }
        final Window window = ((Activity) getContext()).getWindow();
        if (show) {
            wasStatusBar = window.getStatusBarColor();
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) bigLayout.getLayoutParams();
        final float topMargin = layoutParams.topMargin;
        final float leftMargin = layoutParams.leftMargin;
        final float fromX = floatingButtonContainer.getTranslationX() - leftMargin;
        final float fromY = floatingButtonContainer.getTranslationY() - topMargin;
        new SpringAnimation(new FloatValueHolder(show ? 0f : 1000f))
            .setSpring(new SpringForce(1000f)
                .setStiffness(900f)
                .setDampingRatio(1f)
                .setFinalPosition(show ? 1000f : 0f))
            .addUpdateListener((animation, value, velocity) -> {
                float progress = value / 1000f;
                bigLayout.setAlpha(progress);
                bigLayout.setTranslationX(AndroidUtilities.lerp(fromX, 0, progress));
                bigLayout.setTranslationY(AndroidUtilities.lerp(fromY, 0, progress));
                bigLayout.setPivotX(floatingButtonContainer.getTranslationX() - leftMargin + AndroidUtilities.dp(28));
                bigLayout.setPivotY(floatingButtonContainer.getTranslationY() - topMargin + AndroidUtilities.dp(28));
                if (bigLayout.getWidth() != 0) {
                    bigLayout.setScaleX(AndroidUtilities.lerp((float) floatingButtonContainer.getWidth() / bigLayout.getWidth(), 1f, progress));
                }
                if (bigLayout.getHeight() != 0) {
                    bigLayout.setScaleY(AndroidUtilities.lerp((float) floatingButtonContainer.getHeight() / bigLayout.getHeight(), 1f, progress));
                }
                floatingButtonContainer.setTranslationX(AndroidUtilities.lerp(fromX + leftMargin, getWidth() / 2f - AndroidUtilities.dp(28), progress));
                floatingButtonContainer.setTranslationY(AndroidUtilities.lerp(fromY + topMargin, getHeight() / 2f - AndroidUtilities.dp(28), progress));
                floatingButtonContainer.setAlpha(1f - progress);
                window.setStatusBarColor(ColorUtils.blendARGB(wasStatusBar, DIM_STATUS_BAR_COLOR, progress));
                invalidate();
            })
            .addEndListener((animation, canceled, value, velocity) -> {
                floatingButtonContainer.setTranslationX(fromX + leftMargin);
                floatingButtonContainer.setTranslationY(fromY + topMargin);
                if (!show) {
                    bigLayout.setVisibility(GONE);
                }
            })
            .start();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fabXSpring.cancel();
        fabYSpring.cancel();
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        floatingButtonContainer.setTranslationX(clampX(displayMetrics, floatingButtonContainer.getTranslationX() >= displayMetrics.widthPixels / 2f ? FAB_EDGE_RIGHT : FAB_EDGE_LEFT));
        floatingButtonContainer.setTranslationY(clampY(displayMetrics, floatingButtonContainer.getTranslationY()));
        fabXSpring.getSpring().setFinalPosition(floatingButtonContainer.getTranslationX());
        fabYSpring.getSpring().setFinalPosition(floatingButtonContainer.getTranslationY());
    }

    public void showFab() {
        floatingButtonContainer.setVisibility(VISIBLE);
        new SpringAnimation(new FloatValueHolder(0f))
            .setSpring(new SpringForce(1000f).setStiffness(750f).setDampingRatio(0.75f))
            .addUpdateListener((animation, value, velocity) -> {
                float progress = value / 1000f;
                floatingButtonContainer.setPivotX(AndroidUtilities.dp(28));
                floatingButtonContainer.setPivotY(AndroidUtilities.dp(28));
                floatingButtonContainer.setScaleX(progress);
                floatingButtonContainer.setScaleY(progress);
                floatingButtonContainer.setAlpha(MathUtils.clamp(progress, 0f, 1f));
                invalidate();
            })
            .start();
    }

    public void dismiss(Runnable onDismissed) {
        onDismissed.run();
    }
}
