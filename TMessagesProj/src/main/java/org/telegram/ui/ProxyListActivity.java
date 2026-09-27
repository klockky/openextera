/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.ProxyDisableCondition;
import com.exteragram.messenger.proxy.ProxyController;
import com.exteragram.messenger.utils.ui.PopupUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ProxyRotationController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;

import java.util.ArrayList;
import java.util.List;

public class ProxyListActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private static final int MENU_DELETE = 0;
    private static final int MENU_SHARE = 1;
    private static final int MENU_PIN = 2;

    private static final int VIEW_TYPE_SHADOW = 0;
    private static final int VIEW_TYPE_SETTINGS = 1;
    private static final int VIEW_TYPE_HEADER = 2;
    private static final int VIEW_TYPE_CHECK = 3;
    private static final int VIEW_TYPE_INFO = 4;
    private static final int VIEW_TYPE_PROXY = 5;
    private static final int VIEW_TYPE_SLIDE_CHOOSE = 6;
    private static final int VIEW_TYPE_USE_PROXY = 7;

    private static final int PAYLOAD_CHECKED_CHANGED = 0;
    private static final int PAYLOAD_SELECTION_CHANGED = 1;
    private static final int PAYLOAD_SELECTION_MODE_CHANGED = 2;

    private static final ProxyDisableCondition[] PROXY_DISABLE_CONDITIONS = ProxyDisableCondition.values();

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;

    private int currentConnectionState;

    private boolean useProxySettings;

    private int rowCount;
    @Keep
    private int useProxyRow;
    private int useProxyShadowRow;
    private int mainHeaderRow;
    private int connectionsHeaderRow;
    private int proxyStartRow;
    private int proxyEndRow;
    @Keep
    private int proxyAddRow;
    private int proxyShadowRow;
    private int rotationRow;
    private int rotationTimeoutRow;
    private int rotationTimeoutInfoRow;
    private int proxyDisableRow;
    private int proxyDisableShadowRow;
    private int deleteAllRow;

    private ItemTouchHelper itemTouchHelper;
    private NumberTextView selectedCountTextView;
    private ActionBarMenuItem pinMenuItem;
    private ActionBarMenuItem shareMenuItem;
    private ActionBarMenuItem deleteMenuItem;

    private List<SharedConfig.ProxyInfo> selectedItems = new ArrayList<>();
    private List<SharedConfig.ProxyInfo> proxyList = new ArrayList<>();
    private boolean checkedProxies;
    private boolean refreshActionEnabled;

    private final View.OnClickListener refreshActionClickListener = v -> {
        if (refreshActionEnabled) {
            refreshProxyStatus();
        }
    };

    public class TextDetailProxyCell extends FrameLayout {

        private TextView textView;
        private TextView valueTextView;
        private ImageView checkImageView;
        private ImageView reorderImageView;
        private SharedConfig.ProxyInfo currentInfo;
        private Drawable checkDrawable;
        private Drawable pinDrawable;

        private CheckBox2 checkBox;
        private boolean isSelected;
        private boolean isSelectionEnabled;
        private boolean isReorderAvailable;

        private int color;

        public TextDetailProxyCell(Context context) {
            super(context);

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 56 : 21), 10, (LocaleController.isRTL ? 21 : 56), 0));

            valueTextView = new TextView(context);
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            valueTextView.setLines(1);
            valueTextView.setMaxLines(1);
            valueTextView.setSingleLine(true);
            valueTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));
            valueTextView.setEllipsize(TextUtils.TruncateAt.END);
            valueTextView.setPadding(0, 0, 0, 0);
            addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 56 : 21), 35, (LocaleController.isRTL ? 21 : 56), 0));

            checkImageView = new ImageView(context);
            checkImageView.setImageResource(R.drawable.msg_info);
            checkImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3), PorterDuff.Mode.MULTIPLY));
            checkImageView.setScaleType(ImageView.ScaleType.CENTER);
            checkImageView.setContentDescription(getString(R.string.Edit));
            addView(checkImageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 8, 8, 8, 0));
            checkImageView.setOnClickListener(v -> presentFragment(new ProxySettingsActivity(currentInfo)));

            reorderImageView = new ImageView(context);
            reorderImageView.setImageResource(R.drawable.list_reorder);
            reorderImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.MULTIPLY));
            reorderImageView.setScaleType(ImageView.ScaleType.CENTER);
            reorderImageView.setContentDescription(getString(R.string.ProfileBotReorder));
            reorderImageView.setVisibility(GONE);
            addView(reorderImageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 8, 0, 8, 0));

            checkBox = new CheckBox2(context, 21);
            checkBox.setColor(Theme.key_checkbox, Theme.key_radioBackground, Theme.key_checkboxCheck);
            checkBox.setDrawBackgroundAsArc(14);
            checkBox.setVisibility(GONE);
            addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 16, 0, 8, 0));

            setWillNotDraw(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + 1, MeasureSpec.EXACTLY));
        }

        public void setProxy(SharedConfig.ProxyInfo proxyInfo) {
            textView.setText(ProxyController.getInstance().getDisplayName(proxyInfo));
            currentInfo = proxyInfo;
            if (pinDrawable == null) {
                pinDrawable = getContext().getResources().getDrawable(R.drawable.msg_pin_mini).mutate();
                textView.setCompoundDrawablePadding(AndroidUtilities.dp(4));
            }
            if (ProxyController.getInstance().isPinned(proxyInfo)) {
                pinDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_pinnedIcon), PorterDuff.Mode.MULTIPLY));
                if (LocaleController.isRTL) {
                    textView.setCompoundDrawablesWithIntrinsicBounds(null, null, pinDrawable, null);
                } else {
                    textView.setCompoundDrawablesWithIntrinsicBounds(pinDrawable, null, null, null);
                }
            } else {
                textView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            }
        }

        public void updateStatus() {
            int colorKey;
            if (ProxyController.getInstance().getCurrentProxy() == currentInfo && useProxySettings) {
                if (currentConnectionState == ConnectionsManager.ConnectionStateConnected || currentConnectionState == ConnectionsManager.ConnectionStateUpdating) {
                    colorKey = Theme.key_windowBackgroundWhiteBlueText6;
                    if (currentInfo.ping != 0) {
                        valueTextView.setText(getString(R.string.Connected) + ", " + LocaleController.formatString("Ping", R.string.Ping, currentInfo.ping));
                    } else {
                        valueTextView.setText(getString(R.string.Connected));
                    }
                    if (!currentInfo.checking && !currentInfo.available) {
                        currentInfo.availableCheckTime = 0;
                    }
                } else {
                    colorKey = Theme.key_windowBackgroundWhiteGrayText2;
                    valueTextView.setText(getString(R.string.Connecting));
                }
            } else {
                if (currentInfo.checking) {
                    valueTextView.setText(getString(R.string.Checking));
                    colorKey = Theme.key_windowBackgroundWhiteGrayText2;
                } else if (currentInfo.available) {
                    if (currentInfo.ping != 0) {
                        valueTextView.setText(getString(R.string.Available) + ", " + LocaleController.formatString("Ping", R.string.Ping, currentInfo.ping));
                    } else {
                        valueTextView.setText(getString(R.string.Available));
                    }
                    colorKey = Theme.key_windowBackgroundWhiteGreenText;
                } else {
                    valueTextView.setText(getString(R.string.Unavailable));
                    colorKey = Theme.key_text_RedRegular;
                }
            }
            color = Theme.getColor(colorKey);
            valueTextView.setTag(colorKey);
            valueTextView.setTextColor(color);
            if (checkDrawable != null) {
                checkDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            }
        }

        public void setSelectionEnabled(boolean enabled, boolean animated) {
            if (isSelectionEnabled == enabled && animated) {
                return;
            }
            isSelectionEnabled = enabled;

            float fromX = 0, toX = LocaleController.isRTL ? -AndroidUtilities.dp(32) : AndroidUtilities.dp(32);
            if (!animated) {
                float x = enabled ? toX : fromX;
                textView.setTranslationX(x);
                valueTextView.setTranslationX(x);
                checkImageView.setTranslationX(x);
                checkBox.setTranslationX((LocaleController.isRTL ? AndroidUtilities.dp(32) : -AndroidUtilities.dp(32)) + x);
                checkImageView.setVisibility(enabled ? GONE : VISIBLE);
                checkImageView.setAlpha(1f);
                checkImageView.setScaleX(1f);
                checkImageView.setScaleY(1f);
                checkBox.setVisibility(enabled ? VISIBLE : GONE);
                checkBox.setAlpha(1f);
                checkBox.setScaleX(1f);
                checkBox.setScaleY(1f);
                updateReorderHandle(false);
            } else {
                ValueAnimator animator = ValueAnimator.ofFloat(enabled ? 0 : 1, enabled ? 1 : 0).setDuration(200);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.addUpdateListener(animation -> {
                    float val = (float) animation.getAnimatedValue();
                    float x = AndroidUtilities.lerp(fromX, toX, val);
                    textView.setTranslationX(x);
                    valueTextView.setTranslationX(x);
                    checkImageView.setTranslationX(x);
                    checkBox.setTranslationX((LocaleController.isRTL ? AndroidUtilities.dp(32) : -AndroidUtilities.dp(32)) + x);

                    float scale = 0.5f + val * 0.5f;
                    checkBox.setScaleX(scale);
                    checkBox.setScaleY(scale);
                    checkBox.setAlpha(val);

                    scale = 0.5f + (1f - val) * 0.5f;
                    checkImageView.setScaleX(scale);
                    checkImageView.setScaleY(scale);
                    checkImageView.setAlpha(1f - val);
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        if (enabled) {
                            checkBox.setAlpha(0f);
                            checkBox.setVisibility(VISIBLE);
                        } else {
                            checkImageView.setAlpha(0f);
                            checkImageView.setVisibility(VISIBLE);
                        }
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (enabled) {
                            checkImageView.setVisibility(GONE);
                        } else {
                            checkBox.setVisibility(GONE);
                        }
                    }
                });
                animator.start();
                updateReorderHandle(true);
            }
        }

        public void setReorderAvailable(boolean available, boolean animated) {
            if (isReorderAvailable == available) {
                if (!animated) {
                    return;
                }
                final int expectedVisibility = isSelectionEnabled && available ? VISIBLE : GONE;
                if (reorderImageView.getVisibility() == expectedVisibility) {
                    return;
                }
            }
            isReorderAvailable = available;
            updateReorderHandle(animated);
        }

        private void updateReorderHandle(boolean animated) {
            final boolean visible = isSelectionEnabled && isReorderAvailable;
            reorderImageView.animate().cancel();
            if (!animated) {
                reorderImageView.setVisibility(visible ? VISIBLE : GONE);
                reorderImageView.setAlpha(1f);
                reorderImageView.setScaleX(1f);
                reorderImageView.setScaleY(1f);
            } else if (visible) {
                reorderImageView.setVisibility(VISIBLE);
                reorderImageView.setAlpha(0f);
                reorderImageView.setScaleX(0.5f);
                reorderImageView.setScaleY(0.5f);
                reorderImageView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            } else if (reorderImageView.getVisibility() == VISIBLE) {
                reorderImageView.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).withEndAction(() -> {
                    if (!isSelectionEnabled || !isReorderAvailable) {
                        reorderImageView.setVisibility(GONE);
                    }
                    reorderImageView.setAlpha(1f);
                    reorderImageView.setScaleX(1f);
                    reorderImageView.setScaleY(1f);
                }).start();
            }
        }

        public void setItemSelected(boolean selected, boolean animated) {
            if (selected == isSelected && animated) {
                return;
            }
            isSelected = selected;
            checkBox.setChecked(selected, animated);
        }

        public void setChecked(boolean checked) {
            if (checked) {
                if (checkDrawable == null) {
                    checkDrawable = getResources().getDrawable(R.drawable.proxy_check).mutate();
                }
                if (checkDrawable != null) {
                    checkDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                }
                if (LocaleController.isRTL) {
                    valueTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, checkDrawable, null);
                } else {
                    valueTextView.setCompoundDrawablesWithIntrinsicBounds(checkDrawable, null, null, null);
                }
            } else {
                valueTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            }
        }

        public void setValue(CharSequence value) {
            valueTextView.setText(value);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateStatus();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        ProxyController.getInstance().loadProxyList();
        currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState();

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyChangedByRotation);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyCheckDone);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdateConnectionState);

        reloadSettingsFromPreferences();
        updateRows(true);

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxyChangedByRotation);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxyCheckDone);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.ProxySettings));
        if (parentLayout != null && parentLayout.isLayersLayout()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setSections();
        actionBar.setAdaptiveBackground(listView);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        ((DefaultItemAnimator) listView.getItemAnimator()).setTranslationInterpolator(CubicBezierInterpolator.DEFAULT);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                final int position = getProxyPosition(viewHolder);
                if (position == -1) {
                    return 0;
                }
                return makeMovementFlags(canStartReorder(position) ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0, 0);
            }

            @Override
            public boolean canDropOver(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder current, @NonNull RecyclerView.ViewHolder target) {
                final int from = getProxyPosition(current);
                final int to = getProxyPosition(target);
                return from != -1 && to != -1 && isPinnedProxyPos(from) && isPinnedProxyPos(to);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                final int from = getProxyPosition(viewHolder);
                final int to = getProxyPosition(target);
                return from != -1 && to != -1 && movePinnedProxy(from, to);
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                if (actionState != ItemTouchHelper.ACTION_STATE_IDLE && viewHolder != null) {
                    listView.cancelClickRunnables(false);
                    listView.setDraggingChild(viewHolder.itemView);
                    viewHolder.itemView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), Theme.getColor(Theme.key_windowBackgroundWhite)));
                    viewHolder.itemView.bringToFront();
                    listView.invalidate();
                } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    listView.setDraggingChild(null);
                    listView.invalidate();
                }
                super.onSelectedChanged(viewHolder, actionState);
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                listView.setDraggingChild(null);
                viewHolder.itemView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                listView.invalidate();
            }
        });
        itemTouchHelper.attachToRecyclerView(listView);

        listView.setOnItemClickListener((view, adapterPosition) -> {
            final int position = getItemPosition(view, adapterPosition);
            if (position == useProxyRow) {
                if (ProxyController.getInstance().getCurrentProxy() == null) {
                    if (!proxyList.isEmpty()) {
                        ProxyController.getInstance().setCurrentProxy(proxyList.get(0));

                        if (!useProxySettings) {
                            SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                            ProxyController.getInstance().getCurrentProxy().settings.toSharedPreferences(editor);
                            editor.commit();
                        }
                    } else {
                        presentFragment(new ProxySettingsActivity());
                        return;
                    }
                }

                useProxySettings = !useProxySettings;
                updateRows(true);

                TextCheckCell textCheckCell = (TextCheckCell) view;
                textCheckCell.setChecked(useProxySettings);
                textCheckCell.setBackgroundColorAnimated(useProxySettings, Theme.getColor(useProxySettings ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));

                SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                editor.putBoolean("proxy_enabled", useProxySettings);
                editor.apply();

                reapplyCurrentProxySettings();
                NotificationCenter.getGlobalInstance().removeObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                NotificationCenter.getGlobalInstance().addObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);

                for (int a = proxyStartRow; a < proxyEndRow; a++) {
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(a);
                    if (holder != null) {
                        TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                        cell.updateStatus();
                    }
                }
            } else if (position == rotationRow) {
                SharedConfig.proxyRotationEnabled = !SharedConfig.proxyRotationEnabled;
                TextCheckCell textCheckCell = (TextCheckCell) view;
                textCheckCell.setChecked(SharedConfig.proxyRotationEnabled);
                SharedConfig.saveConfig();
                updateRows(true);
            } else if (position == proxyDisableRow) {
                showProxyDisableDialog();
            } else if (position >= proxyStartRow && position < proxyEndRow) {
                if (!selectedItems.isEmpty()) {
                    listAdapter.toggleSelected(position);
                    return;
                }
                SharedConfig.ProxyInfo info = proxyList.get(position - proxyStartRow);
                boolean wasEnabled = useProxySettings;
                useProxySettings = true;
                SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                info.settings.toSharedPreferences(editor);
                editor.putBoolean("proxy_enabled", useProxySettings);
                editor.apply();
                ProxyController.getInstance().setCurrentProxy(info);
                for (int a = proxyStartRow; a < proxyEndRow; a++) {
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(a);
                    if (holder != null) {
                        TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                        cell.setChecked(cell.currentInfo == info);
                        cell.updateStatus();
                    }
                }
                updateRows(false);
                RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(useProxyRow);
                if (holder != null) {
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    textCheckCell.setChecked(true);
                    if (!wasEnabled) {
                        textCheckCell.setBackgroundColorAnimated(true, Theme.getColor(Theme.key_windowBackgroundChecked));
                    }
                }
                reapplyCurrentProxySettings();
            } else if (position == proxyAddRow) {
                presentFragment(new ProxySettingsActivity());
            } else if (position == deleteAllRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(getString(R.string.DeleteAllProxiesConfirm));
                builder.setNegativeButton(getString(R.string.Cancel), null);
                builder.setTitle(getString(R.string.DeleteProxyTitle));
                builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
                    ProxyController proxyController = ProxyController.getInstance();
                    proxyController.clearAll();
                    for (SharedConfig.ProxyInfo info : proxyList) {
                        proxyController.deleteProxy(info);
                    }
                    useProxySettings = false;
                    NotificationCenter.getGlobalInstance().removeObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                    NotificationCenter.getGlobalInstance().addObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                    updateRows(true);
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(useProxyRow, PAYLOAD_CHECKED_CHANGED);
                        listAdapter.clearSelected();
                    }
                });
                AlertDialog dialog = builder.create();
                showDialog(dialog);
                TextView button = (TextView) dialog.getButton(Dialog.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                }
            }
        });
        listView.setOnItemLongClickListener((view, adapterPosition) -> {
            final int position = getItemPosition(view, adapterPosition);
            if (position < proxyStartRow || position >= proxyEndRow) {
                return false;
            }
            if (selectedItems.isEmpty()) {
                listAdapter.toggleSelected(position);
                return true;
            }
            if (canStartReorder(position) && itemTouchHelper != null) {
                listAdapter.selectForReorder(position);
                listView.cancelClickRunnables(true);
                view.setPressed(false);
                view.jumpDrawablesToCurrentState();
                RecyclerView.ViewHolder holder = listView.findContainingViewHolder(view);
                if (getProxyPosition(holder) == position) {
                    itemTouchHelper.startDrag(holder);
                }
                return true;
            }
            listAdapter.toggleSelected(position);
            return true;
        });

        ActionBarMenu actionMode = actionBar.createActionMode();
        selectedCountTextView = new NumberTextView(actionMode.getContext());
        selectedCountTextView.setTextSize(18);
        selectedCountTextView.setTypeface(AndroidUtilities.bold());
        selectedCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedCountTextView.setOnTouchListener((v, event) -> true);

        pinMenuItem = actionMode.addItemWithWidth(MENU_PIN, R.drawable.msg_pin, AndroidUtilities.dp(54));
        shareMenuItem = actionMode.addItemWithWidth(MENU_SHARE, R.drawable.msg_share, AndroidUtilities.dp(54));
        shareMenuItem.setContentDescription(getString(R.string.StickersShare));
        deleteMenuItem = actionMode.addItemWithWidth(MENU_DELETE, R.drawable.msg_delete, AndroidUtilities.dp(54));
        deleteMenuItem.setContentDescription(getString(R.string.Delete));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (selectedItems.isEmpty()) {
                        finishFragment();
                    } else {
                        listAdapter.clearSelected();
                    }
                } else if (id == MENU_DELETE) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(getString(selectedItems.size() > 1 ? R.string.DeleteProxyMultiConfirm : R.string.DeleteProxyConfirm));
                    builder.setNegativeButton(getString(R.string.Cancel), null);
                    builder.setTitle(getString(R.string.DeleteProxyTitle));
                    builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
                        ProxyController proxyController = ProxyController.getInstance();
                        for (SharedConfig.ProxyInfo info : selectedItems) {
                            proxyController.deleteProxy(info);
                        }
                        if (proxyController.getCurrentProxy() == null) {
                            useProxySettings = false;
                        }
                        NotificationCenter.getGlobalInstance().removeObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                        NotificationCenter.getGlobalInstance().addObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                        updateRows(true);
                        if (listAdapter != null) {
                            if (ProxyController.getInstance().getCurrentProxy() == null) {
                                listAdapter.notifyItemChanged(useProxyRow, PAYLOAD_CHECKED_CHANGED);
                            }
                            listAdapter.clearSelected();
                        }
                    });
                    AlertDialog dialog = builder.create();
                    showDialog(dialog);
                    TextView button = (TextView) dialog.getButton(Dialog.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    }
                } else if (id == MENU_SHARE) {
                    StringBuilder links = new StringBuilder();
                    for (SharedConfig.ProxyInfo info : selectedItems) {
                        if (links.length() > 0) {
                            links.append("\n\n");
                        }
                        links.append(ProxyController.getInstance().buildShareLink(info));
                    }
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, links.toString());
                    Intent chooserIntent = Intent.createChooser(shareIntent, getString(selectedItems.size() > 1 ? R.string.ShareLinks : R.string.ShareLink));
                    chooserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(chooserIntent);
                    if (listAdapter != null) {
                        listAdapter.clearSelected();
                    }
                } else if (id == MENU_PIN) {
                    toggleSelectedPins();
                }
            }
        });

        return fragmentView;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (!selectedItems.isEmpty()) {
            if (invoked) {
                listAdapter.clearSelected();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    private void showProxyDisableDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final CharSequence[] items = new CharSequence[PROXY_DISABLE_CONDITIONS.length];
        final boolean[] checked = new boolean[PROXY_DISABLE_CONDITIONS.length];
        for (int i = 0; i < PROXY_DISABLE_CONDITIONS.length; i++) {
            items[i] = getProxyDisableConditionName(PROXY_DISABLE_CONDITIONS[i]);
            checked[i] = ExteraConfig.isProxyDisabledOn(PROXY_DISABLE_CONDITIONS[i]);
        }
        PopupUtils.showMultiSelectDialog(items, checked, getString(R.string.ProxyDisableOn), getParentActivity(), result -> {
            for (int i = 0; i < PROXY_DISABLE_CONDITIONS.length; i++) {
                ExteraConfig.setProxyDisabledOn(PROXY_DISABLE_CONDITIONS[i], result[i]);
            }
            ApplicationLoader.checkProxyForNetworkState();
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(proxyDisableRow);
            }
        }, getResourceProvider());
    }

    private String getProxyDisableConditionName(ProxyDisableCondition condition) {
        switch (condition) {
            case VPN:
                return getString(R.string.ProxyDisableOnVpn);
            case MOBILE_DATA:
                return getString(R.string.ProxyDisableOnMobileData);
            default:
                return getString(R.string.ProxyDisableOnWiFi);
        }
    }

    private String getProxyDisableValue() {
        ArrayList<String> conditions = new ArrayList<>();
        for (ProxyDisableCondition condition : PROXY_DISABLE_CONDITIONS) {
            if (ExteraConfig.isProxyDisabledOn(condition)) {
                conditions.add(getProxyDisableConditionName(condition));
            }
        }
        if (conditions.isEmpty()) {
            return getString(R.string.ProxyDisableNever);
        }
        if (conditions.size() == PROXY_DISABLE_CONDITIONS.length) {
            return getString(R.string.ProxyDisableAlways);
        }
        return TextUtils.join(", ", conditions);
    }

    private void reloadSettingsFromPreferences() {
        useProxySettings = MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false) && !ProxyController.getInstance().getProxyList().isEmpty();
    }

    private boolean isProxyPosition(int position) {
        return position >= proxyStartRow && position < proxyEndRow;
    }

    private int getProxyPosition(RecyclerView.ViewHolder holder) {
        if (holder == null) {
            return -1;
        }
        int position = holder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION) {
            position = holder.getLayoutPosition();
        }
        if (position == RecyclerView.NO_POSITION && listView != null) {
            position = listView.getChildAdapterPosition(holder.itemView);
        }
        return isProxyPosition(position) ? position : -1;
    }

    private int getItemPosition(View view, int fallbackPosition) {
        if (view != null && listView != null) {
            RecyclerView.ViewHolder holder = listView.findContainingViewHolder(view);
            if (holder != null) {
                int position = holder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    position = holder.getLayoutPosition();
                }
                if (position != RecyclerView.NO_POSITION) {
                    return position;
                }
            }
            int position = listView.getChildAdapterPosition(view);
            if (position != RecyclerView.NO_POSITION) {
                return position;
            }
        }
        return fallbackPosition;
    }

    private SharedConfig.ProxyInfo getProxyAtPosition(int position) {
        if (isProxyPosition(position)) {
            return proxyList.get(position - proxyStartRow);
        }
        return null;
    }

    private boolean isPinnedProxyPos(int position) {
        return ProxyController.getInstance().isPinned(getProxyAtPosition(position));
    }

    private boolean canStartReorder(int position) {
        return actionBar.isActionModeShowed() && ProxyController.getInstance().getPinnedCount() > 1 && isPinnedProxyPos(position);
    }

    private boolean canShowReorderHandle(SharedConfig.ProxyInfo info) {
        return info != null && actionBar != null && actionBar.isActionModeShowed() && !selectedItems.isEmpty() && ProxyController.getInstance().getPinnedCount() > 1 && ProxyController.getInstance().isPinned(info);
    }

    private boolean movePinnedProxy(int from, int to) {
        if (!isPinnedProxyPos(from) || !isPinnedProxyPos(to) || from == to) {
            return false;
        }
        final SharedConfig.ProxyInfo fromInfo = getProxyAtPosition(from);
        final SharedConfig.ProxyInfo toInfo = getProxyAtPosition(to);
        if (fromInfo == null || toInfo == null || !ProxyController.getInstance().movePinnedProxy(fromInfo, toInfo)) {
            return false;
        }
        proxyList.remove(from - proxyStartRow);
        proxyList.add(to - proxyStartRow, fromInfo);
        listAdapter.notifyItemMoved(from, to);
        listAdapter.notifyItemRangeChanged(Math.min(from, to), Math.abs(from - to) + 1, PAYLOAD_SELECTION_CHANGED);
        return true;
    }

    private int getSelectedPinAction() {
        return ProxyController.getInstance().getSelectedPinAction(selectedItems);
    }

    private void updatePinAction() {
        if (pinMenuItem == null) {
            return;
        }
        final int action = getSelectedPinAction();
        if (action == 0) {
            pinMenuItem.setVisibility(View.GONE);
        } else {
            pinMenuItem.setVisibility(View.VISIBLE);
            pinMenuItem.setIcon(action == 2 ? R.drawable.msg_unpin : R.drawable.msg_pin);
        }
    }

    private void toggleSelectedPins() {
        ProxyController.PinOperationResult result = ProxyController.getInstance().applySelectedPinAction(selectedItems);
        if (result == ProxyController.PinOperationResult.LIMIT_REACHED) {
            BulletinFactory.of(this).createErrorBulletin(LocaleController.formatString("ProxyPinLimitReached", R.string.ProxyPinLimitReached, ProxyController.getInstance().getMaxPinnedProxies())).show();
        } else if (result == ProxyController.PinOperationResult.CHANGED) {
            updateRows(true);
            if (listAdapter != null) {
                listAdapter.clearSelected();
            }
        }
    }

    private boolean hasCheckingProxies() {
        for (SharedConfig.ProxyInfo info : proxyList) {
            if (info.checking) {
                return true;
            }
        }
        return false;
    }

    private boolean isRefreshActionEnabled() {
        return proxyStartRow != -1 && !hasCheckingProxies();
    }

    private void updateRefreshActionState(boolean force) {
        final boolean enabled = isRefreshActionEnabled();
        if (force || refreshActionEnabled != enabled) {
            refreshActionEnabled = enabled;
            notifyConnectionsHeaderChanged();
        }
    }

    private void bindRefreshActionView(SimpleTextView textView) {
        textView.setText(getString(hasCheckingProxies() ? R.string.Checking : R.string.Refresh));
        final int colorKey;
        final float alpha;
        if (refreshActionEnabled) {
            colorKey = Theme.isCurrentThemeDark() ? Theme.key_windowBackgroundWhiteGrayText2 : Theme.key_windowBackgroundWhiteBlackText;
            alpha = Theme.isCurrentThemeDark() ? 1f : 0.75f;
        } else {
            colorKey = Theme.key_windowBackgroundWhiteGrayText3;
            alpha = 0.55f;
        }
        final Object tag = textView.getTag();
        if (!(tag instanceof Integer) || (Integer) tag != colorKey) {
            textView.setTag(colorKey);
            textView.setTextColor(Theme.getColor(colorKey));
        }
        if (textView.getAlpha() != alpha) {
            textView.setAlpha(alpha);
        }
        if (textView.isClickable() != refreshActionEnabled) {
            textView.setClickable(refreshActionEnabled);
        }
        textView.setOnClickListener(refreshActionClickListener);
    }

    private void notifyConnectionsHeaderChanged() {
        if (listAdapter != null && connectionsHeaderRow >= 0) {
            listAdapter.notifyItemChanged(connectionsHeaderRow);
        }
    }

    private void refreshProxyStatus() {
        if (proxyStartRow == -1 || hasCheckingProxies()) {
            return;
        }
        checkedProxies = false;
        boolean started = false;
        for (int a = 0, count = proxyList.size(); a < count; a++) {
            final SharedConfig.ProxyInfo proxyInfo = proxyList.get(a);
            if (proxyInfo.checking) {
                continue;
            }
            started = true;
            proxyInfo.checking = true;
            ConnectionsManager.getInstance(currentAccount).checkProxy(proxyInfo.settings, time -> AndroidUtilities.runOnUIThread(() -> {
                proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                proxyInfo.checking = false;
                if (time == -1) {
                    proxyInfo.available = false;
                    proxyInfo.ping = 0;
                } else {
                    proxyInfo.ping = time;
                    proxyInfo.available = true;
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);
            }));
        }
        if (started) {
            if (listAdapter != null) {
                listAdapter.notifyItemRangeChanged(proxyStartRow, proxyEndRow - proxyStartRow);
            }
            updateRefreshActionState(false);
        }
    }

    private void updateRows(boolean notify) {
        rowCount = 0;
        useProxyRow = rowCount++;
        useProxyShadowRow = rowCount++;
        mainHeaderRow = rowCount++;

        final SharedConfig.ProxyInfo currentProxy = ProxyController.getInstance().getCurrentProxy();
        if (useProxySettings && currentProxy != null && ProxyController.getInstance().getProxyList().size() > 1) {
            rotationRow = rowCount++;
            if (SharedConfig.proxyRotationEnabled) {
                rotationTimeoutRow = rowCount++;
                rotationTimeoutInfoRow = rowCount++;
            } else {
                rotationTimeoutRow = -1;
                rotationTimeoutInfoRow = -1;
            }
        } else {
            rotationRow = -1;
            rotationTimeoutRow = -1;
            rotationTimeoutInfoRow = -1;
        }

        proxyDisableRow = rowCount++;
        proxyDisableShadowRow = rowCount++;
        connectionsHeaderRow = rowCount++;

        if (notify) {
            proxyList.clear();
            proxyList.addAll(ProxyController.getInstance().getProxyList());

            boolean checking = false;
            if (!checkedProxies) {
                for (SharedConfig.ProxyInfo info : proxyList) {
                    if (info.checking || info.availableCheckTime == 0) {
                        checking = true;
                        break;
                    }
                }
                if (!checking) {
                    checkedProxies = true;
                }
            }

            ProxyController.getInstance().sortProxyList(proxyList, checking, ProxyController.getInstance().getCurrentProxy());
        }
        if (!proxyList.isEmpty()) {
            proxyStartRow = rowCount;
            rowCount += proxyList.size();
            proxyEndRow = rowCount;
        } else {
            proxyStartRow = -1;
            proxyEndRow = -1;
        }
        proxyAddRow = rowCount++;
        proxyShadowRow = rowCount++;
        if (proxyList.size() >= 10) {
            deleteAllRow = rowCount++;
        } else {
            deleteAllRow = -1;
        }
        refreshActionEnabled = isRefreshActionEnabled();
        if (notify && listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void reapplyCurrentProxySettings() {
        final SharedConfig.ProxyInfo currentProxy = ProxyController.getInstance().getCurrentProxy();
        if (currentProxy != null) {
            ConnectionsManager.setProxySettings(useProxySettings, currentProxy.settings);
        }
    }

    private void updateVisibleCheckCellColors() {
        if (listView == null) {
            return;
        }
        listView.forAllChild(view -> {
            if (view instanceof TextCheckCell) {
                TextCheckCell cell = (TextCheckCell) view;
                if (listView.getChildAdapterPosition(view) == useProxyRow) {
                    cell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
                    cell.setBackgroundColor(Theme.getColor(useProxySettings ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
                } else {
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                }
            }
        });
    }

    @Override
    public void onDialogDismiss(Dialog dialog) {
        DownloadController.getInstance(currentAccount).checkAutodownloadSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            reloadSettingsFromPreferences();
            updateRows(true);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxyChangedByRotation) {
            listView.forAllChild(view -> {
                RecyclerView.ViewHolder holder = listView.getChildViewHolder(view);
                if (holder.itemView instanceof TextDetailProxyCell) {
                    TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                    cell.setChecked(cell.currentInfo == ProxyController.getInstance().getCurrentProxy());
                    cell.updateStatus();
                }
            });
            updateRows(false);
        } else if (id == NotificationCenter.proxySettingsChanged) {
            reloadSettingsFromPreferences();
            ProxyController.getInstance().loadProxyList();
            updateRows(true);
        } else if (id == NotificationCenter.didUpdateConnectionState) {
            int state = ConnectionsManager.getInstance(account).getConnectionState();
            if (currentConnectionState != state) {
                currentConnectionState = state;
                final SharedConfig.ProxyInfo currentProxy = ProxyController.getInstance().getCurrentProxy();
                if (listView != null && currentProxy != null) {
                    int idx = proxyList.indexOf(currentProxy);
                    if (idx >= 0) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(idx + proxyStartRow);
                        if (holder != null) {
                            TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                            cell.updateStatus();
                        }
                    }

                    if (currentConnectionState == ConnectionsManager.ConnectionStateConnected) {
                        updateRows(true);
                    }
                }
            }
        } else if (id == NotificationCenter.proxyCheckDone) {
            if (listView != null) {
                SharedConfig.ProxyInfo proxyInfo = (SharedConfig.ProxyInfo) args[0];
                int idx = proxyList.indexOf(proxyInfo);
                if (idx >= 0) {
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(idx + proxyStartRow);
                    if (holder != null) {
                        TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                        cell.updateStatus();
                    }
                }

                boolean checking = false;
                if (!checkedProxies) {
                    for (SharedConfig.ProxyInfo info : proxyList) {
                        if (info.checking || info.availableCheckTime == 0) {
                            checking = true;
                            break;
                        }
                    }
                    if (!checking) {
                        checkedProxies = true;
                    }
                }
                if (!checking) {
                    updateRows(true);
                } else {
                    updateRefreshActionState(false);
                }
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
            setHasStableIds(true);
        }

        public void toggleSelected(int position) {
            final SharedConfig.ProxyInfo info = getProxyAtPosition(position);
            if (info == null) {
                return;
            }
            if (selectedItems.contains(info)) {
                selectedItems.remove(info);
            } else {
                selectedItems.add(info);
            }
            notifyItemChanged(position, PAYLOAD_SELECTION_CHANGED);
            checkActionMode();
        }

        public void selectForReorder(int position) {
            final SharedConfig.ProxyInfo info = getProxyAtPosition(position);
            if (info == null) {
                return;
            }
            if (selectedItems.size() != 1 || !selectedItems.contains(info)) {
                selectedItems.clear();
                selectedItems.add(info);
                notifyItemRangeChanged(proxyStartRow, proxyEndRow - proxyStartRow, PAYLOAD_SELECTION_CHANGED);
            } else {
                notifyItemChanged(position, PAYLOAD_SELECTION_CHANGED);
            }
            checkActionMode();
        }

        public void clearSelected() {
            selectedItems.clear();
            notifyItemRangeChanged(proxyStartRow, proxyEndRow - proxyStartRow, PAYLOAD_SELECTION_CHANGED);
            checkActionMode();
        }

        private void checkActionMode() {
            int selectedCount = selectedItems.size();
            boolean actionModeShowed = actionBar.isActionModeShowed();
            if (selectedCount > 0) {
                selectedCountTextView.setNumber(selectedCount, actionModeShowed);
                updatePinAction();
                if (!actionModeShowed) {
                    actionBar.showActionMode();
                    notifyItemRangeChanged(proxyStartRow, proxyEndRow - proxyStartRow, PAYLOAD_SELECTION_MODE_CHANGED);
                }
            } else if (actionModeShowed) {
                actionBar.hideActionMode();
                notifyItemRangeChanged(proxyStartRow, proxyEndRow - proxyStartRow, PAYLOAD_SELECTION_MODE_CHANGED);
            }
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_SETTINGS: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    textCell.setBetterLayout(position == proxyDisableRow);
                    if (position == proxyAddRow) {
                        textCell.setText(getString(R.string.AddProxy), deleteAllRow != -1);
                    } else if (position == deleteAllRow) {
                        textCell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                        textCell.setText(getString(R.string.DeleteAllProxies), false);
                    } else if (position == proxyDisableRow) {
                        textCell.setTextAndValue(getString(R.string.ProxyDisableOn), getProxyDisableValue(), false);
                    }
                    break;
                }
                case VIEW_TYPE_HEADER: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    SimpleTextView textView2 = headerCell.getTextView2();
                    if (textView2 != null) {
                        textView2.setOnClickListener(null);
                        textView2.setBackground(null);
                        textView2.setPadding(0, 0, 0, 0);
                        textView2.setTranslationY(0);
                    }
                    if (position == connectionsHeaderRow) {
                        headerCell.setText(getString(R.string.ProxyConnections));
                        headerCell.setText2(getString(hasCheckingProxies() ? R.string.Checking : R.string.Refresh));
                        if (textView2 != null) {
                            textView2.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                            textView2.setTranslationY(-AndroidUtilities.dpf2(4));
                            textView2.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3), 0.12f), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
                            bindRefreshActionView(textView2);
                        }
                    } else if (position == mainHeaderRow) {
                        headerCell.setText(getString(R.string.General));
                        headerCell.setText2(null);
                    }
                    break;
                }
                case VIEW_TYPE_CHECK: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == rotationRow) {
                        checkCell.setTextAndCheck(getString(R.string.UseProxyRotation), SharedConfig.proxyRotationEnabled, SharedConfig.proxyRotationEnabled);
                    }
                    break;
                }
                case VIEW_TYPE_INFO: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == rotationTimeoutInfoRow) {
                        cell.setText(getString(R.string.ProxyRotationTimeoutInfo));
                    }
                    break;
                }
                case VIEW_TYPE_PROXY: {
                    TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                    SharedConfig.ProxyInfo info = proxyList.get(position - proxyStartRow);
                    cell.setProxy(info);
                    cell.setChecked(ProxyController.getInstance().getCurrentProxy() == info);
                    cell.setItemSelected(selectedItems.contains(info), false);
                    cell.setReorderAvailable(canShowReorderHandle(info), false);
                    cell.setSelectionEnabled(!selectedItems.isEmpty(), false);
                    break;
                }
                case VIEW_TYPE_SLIDE_CHOOSE: {
                    if (position == rotationTimeoutRow) {
                        SlideChooseView chooseView = (SlideChooseView) holder.itemView;
                        ArrayList<Integer> timeouts = new ArrayList<>(ProxyRotationController.ROTATION_TIMEOUTS);
                        String[] values = new String[timeouts.size()];
                        for (int i = 0; i < timeouts.size(); i++) {
                            values[i] = LocaleController.formatString(R.string.ProxyRotationTimeoutSeconds, timeouts.get(i));
                        }
                        chooseView.setCallback(index -> {
                            SharedConfig.proxyRotationTimeout = index;
                            SharedConfig.saveConfig();
                        });
                        chooseView.setOptions(SharedConfig.proxyRotationTimeout, values);
                    }
                    break;
                }
                case VIEW_TYPE_USE_PROXY: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    checkCell.setTextAndCheck(getString(R.string.UseProxySettings), useProxySettings, false);
                    checkCell.setBackgroundColor(Theme.getColor(useProxySettings ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
                    break;
                }
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List payloads) {
            if (holder.getItemViewType() == VIEW_TYPE_PROXY && !payloads.isEmpty()) {
                TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                SharedConfig.ProxyInfo info = proxyList.get(position - proxyStartRow);
                if (payloads.contains(PAYLOAD_SELECTION_CHANGED)) {
                    cell.setItemSelected(selectedItems.contains(info), true);
                    cell.setReorderAvailable(canShowReorderHandle(info), true);
                }
                if (payloads.contains(PAYLOAD_SELECTION_MODE_CHANGED)) {
                    cell.setReorderAvailable(canShowReorderHandle(info), true);
                    cell.setSelectionEnabled(!selectedItems.isEmpty(), true);
                }
            } else if ((holder.getItemViewType() == VIEW_TYPE_CHECK || holder.getItemViewType() == VIEW_TYPE_USE_PROXY) && payloads.contains(PAYLOAD_CHECKED_CHANGED)) {
                TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                if (position == useProxyRow) {
                    checkCell.setChecked(useProxySettings);
                    checkCell.setBackgroundColorAnimated(useProxySettings, Theme.getColor(useProxySettings ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
                } else if (position == rotationRow) {
                    checkCell.setChecked(SharedConfig.proxyRotationEnabled);
                }
            } else {
                super.onBindViewHolder(holder, position, payloads);
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            if (viewType == VIEW_TYPE_CHECK || viewType == VIEW_TYPE_USE_PROXY) {
                TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                int position = holder.getAdapterPosition();
                if (position == useProxyRow) {
                    checkCell.setChecked(useProxySettings);
                    checkCell.setBackgroundColor(Theme.getColor(useProxySettings ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
                } else if (position == rotationRow) {
                    checkCell.setChecked(SharedConfig.proxyRotationEnabled);
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == useProxyRow || position == proxyDisableRow || position == rotationRow || position == proxyAddRow || position == deleteAllRow || position >= proxyStartRow && position < proxyEndRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_SHADOW:
                    view = new ShadowSectionCell(mContext);
                    break;
                case VIEW_TYPE_SETTINGS:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(mContext, Theme.key_windowBackgroundWhiteBlueHeader, 21, 6, true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_INFO:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case VIEW_TYPE_SLIDE_CHOOSE:
                    view = new SlideChooseView(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_USE_PROXY: {
                    TextCheckCell checkCell = new TextCheckCell(mContext);
                    checkCell.setDrawCheckRipple(true);
                    checkCell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
                    checkCell.setTypeface(AndroidUtilities.bold());
                    checkCell.setHeight(56);
                    view = checkCell;
                    break;
                }
                case VIEW_TYPE_PROXY:
                default:
                    view = new TextDetailProxyCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public long getItemId(int position) {
            // Random stable ids, could be anything non-repeating
            if (position == useProxyShadowRow) {
                return -1;
            } else if (position == proxyShadowRow) {
                return -2;
            } else if (position == proxyAddRow) {
                return -3;
            } else if (position == useProxyRow) {
                return -4;
            } else if (position == mainHeaderRow) {
                return -16;
            } else if (position == proxyDisableRow) {
                return -12;
            } else if (position == proxyDisableShadowRow) {
                return -17;
            } else if (position == connectionsHeaderRow) {
                return -6;
            } else if (position == deleteAllRow) {
                return -8;
            } else if (position == rotationRow) {
                return -9;
            } else if (position == rotationTimeoutRow) {
                return -10;
            } else if (position == rotationTimeoutInfoRow) {
                return -11;
            } else if (position >= proxyStartRow && position < proxyEndRow) {
                return proxyList.get(position - proxyStartRow).hashCode();
            } else {
                return -7;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == useProxyShadowRow || position == proxyDisableShadowRow || position == proxyShadowRow) {
                return VIEW_TYPE_SHADOW;
            } else if (position == proxyAddRow || position == deleteAllRow || position == proxyDisableRow) {
                return VIEW_TYPE_SETTINGS;
            } else if (position == useProxyRow) {
                return VIEW_TYPE_USE_PROXY;
            } else if (position == rotationRow) {
                return VIEW_TYPE_CHECK;
            } else if (position == connectionsHeaderRow || position == mainHeaderRow) {
                return VIEW_TYPE_HEADER;
            } else if (position == rotationTimeoutRow) {
                return VIEW_TYPE_SLIDE_CHOOSE;
            } else if (position >= proxyStartRow && position < proxyEndRow) {
                return VIEW_TYPE_PROXY;
            } else {
                return VIEW_TYPE_INFO;
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = this::updateVisibleCheckCellColors;
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, TextDetailProxyCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailProxyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText6));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGreenText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_text_RedRegular));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"checkImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"reorderImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{HeaderCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{HeaderCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_windowBackgroundChecked));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_windowBackgroundUnchecked));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_windowBackgroundCheckText));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_switchTrackBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_switchTrackBlueChecked));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_switchTrackBlueThumb));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_switchTrackBlueThumbChecked));

        return themeDescriptions;
    }
}
