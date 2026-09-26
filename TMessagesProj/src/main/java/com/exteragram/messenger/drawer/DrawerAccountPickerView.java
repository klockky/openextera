package com.exteragram.messenger.drawer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.api.dto.BadgeDTO;
import com.exteragram.messenger.badges.BadgesController;
import com.exteragram.messenger.utils.ui.AccountsUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;

import java.util.ArrayList;
import java.util.Collections;

public class DrawerAccountPickerView extends FrameLayout {

    private static final int COLOR_KEY_BACKGROUND = Theme.key_windowBackgroundGray;
    private static final int COLOR_KEY_SELECTOR = Theme.key_listSelector;
    private static final int COLOR_KEY_SURFACE = Theme.key_windowBackgroundWhite;
    private static final int COLOR_KEY_TEXT = Theme.key_windowBackgroundWhiteBlackText;
    private static final int COLOR_KEY_STATUS = Theme.key_profile_verifiedBackground;
    private static final int COLOR_KEY_ACCENT = Theme.key_featuredStickers_addButton;
    private static final int COLOR_KEY_ADD_ICON = Theme.key_featuredStickers_buttonText;

    private static final int VIEW_TYPE_ACCOUNT = 0;
    private static final int VIEW_TYPE_ADD_ACCOUNT = 1;

    private final ArrayList<Integer> accounts = new ArrayList<>();
    private final AccountAdapter adapter;
    private final RecyclerView recyclerView;
    private final ItemTouchHelper itemTouchHelper;
    private final FrameLayout clipWrapper;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clipMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bgRect = new RectF();
    private final float cornerRadius = AndroidUtilities.dp(16);
    private final Paint topGradientPaint = new Paint();
    private final Paint bottomGradientPaint = new Paint();
    private LinearGradient topGradient;
    private LinearGradient bottomGradient;
    private int lastHeight;

    private BadgeDTO badgeOverride;
    private View draggingItemView;
    private ValueAnimator expandAnimator;
    private int currentAnimatedHeight = -1;
    private boolean expanded = MessagesController.getGlobalMainSettings().getBoolean("accountsShown", true);

    private OnAccountLongClick onAccountLongClick;
    private Runnable onAccountSelected;

    @FunctionalInterface
    public interface OnAccountLongClick {
        void onLongClick(int account, View view);
    }

    public DrawerAccountPickerView(Context context) {
        super(context);

        clipMaskPaint.setStyle(Paint.Style.FILL);
        clipMaskPaint.setColor(Color.BLACK);
        clipMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        topGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        bottomGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        clipWrapper = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int maxHeight = getMaxListHeight();
                int width = MeasureSpec.getSize(widthMeasureSpec);
                measureChildren(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY));
                if (currentAnimatedHeight >= 0) {
                    setMeasuredDimension(width, currentAnimatedHeight);
                    return;
                }
                int height = MeasureSpec.getSize(heightMeasureSpec);
                if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || height > maxHeight) {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                bgPaint.setColor(Theme.getColor(COLOR_KEY_BACKGROUND));
                bgRect.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint);

                int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
                super.dispatchDraw(canvas);

                if (topGradient == null || getHeight() != lastHeight) {
                    lastHeight = getHeight();
                    topGradient = new LinearGradient(0, 0, 0, AndroidUtilities.dp(16), new int[]{Color.BLACK, 0}, null, Shader.TileMode.CLAMP);
                    topGradientPaint.setShader(topGradient);
                    bottomGradient = new LinearGradient(0, getHeight(), 0, getHeight() - AndroidUtilities.dp(16), new int[]{Color.BLACK, 0}, null, Shader.TileMode.CLAMP);
                    bottomGradientPaint.setShader(bottomGradient);
                }

                float fadeSize = AndroidUtilities.dp(16);
                int scrollOffset = recyclerView.computeVerticalScrollOffset();
                int scrollRemaining = Math.max(0, recyclerView.computeVerticalScrollRange() - recyclerView.computeVerticalScrollExtent() - scrollOffset);
                float topAlpha = Math.min(1f, Math.max(0f, scrollOffset / fadeSize));
                float bottomAlpha = Math.min(1f, scrollRemaining / fadeSize);
                if (topAlpha > 0) {
                    topGradientPaint.setAlpha((int) (topAlpha * 255));
                    canvas.drawRect(0, 0, getWidth(), AndroidUtilities.dp(16), topGradientPaint);
                }
                if (bottomAlpha > 0) {
                    bottomGradientPaint.setAlpha((int) (bottomAlpha * 255));
                    canvas.drawRect(0, getHeight() - AndroidUtilities.dp(16), getWidth(), getHeight(), bottomGradientPaint);
                }
                canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, clipMaskPaint);
                canvas.restoreToCount(saveCount);
            }
        };
        clipWrapper.setOutlineProvider(ViewOutlineProviderImpl.boundsWithRoundRect(cornerRadius));
        clipWrapper.setClipToOutline(true);
        addView(clipWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.TOP, 12, 0, 12, 0));

        adapter = new AccountAdapter();
        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);
        final int spacing = AndroidUtilities.dp(4);
        recyclerView.setPadding(spacing, spacing, spacing, spacing);
        recyclerView.setClipToPadding(false);
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                if (position < 0 || position >= state.getItemCount() - 1) {
                    return;
                }
                outRect.bottom = spacing;
            }
        });
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setVerticalScrollBarEnabled(false);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                clipWrapper.invalidate();
            }
        });
        clipWrapper.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final RecyclerView.ChildDrawingOrderCallback drawDraggingOnTop = (childCount, i) -> {
            if (draggingItemView != null) {
                int draggingIndex = recyclerView.indexOfChild(draggingItemView);
                if (draggingIndex >= 0) {
                    if (i == childCount - 1) {
                        return draggingIndex;
                    }
                    if (i >= draggingIndex) {
                        return i + 1;
                    }
                }
            }
            return i;
        };
        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (viewHolder.getAdapterPosition() >= accounts.size()) {
                    return 0;
                }
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = source.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                if (fromPosition >= accounts.size() || toPosition >= accounts.size()) {
                    return false;
                }
                adapter.swapElements(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || viewHolder == null) {
                    return;
                }
                draggingItemView = viewHolder.itemView;
                draggingItemView.setPressed(false);
                draggingItemView.jumpDrawablesToCurrentState();
                recyclerView.setChildDrawingOrderCallback(drawDraggingOnTop);
                recyclerView.invalidate();
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                viewHolder.itemView.setTranslationX(dX);
                viewHolder.itemView.setTranslationY(dY);
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                viewHolder.itemView.setTranslationX(0);
                viewHolder.itemView.setTranslationY(0);
                viewHolder.itemView.setPressed(false);
                if (draggingItemView == viewHolder.itemView) {
                    draggingItemView = null;
                }
                recyclerView.setChildDrawingOrderCallback(null);
                recyclerView.invalidate();
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);

        if (expanded) {
            loadAccounts();
            setVisibility(View.VISIBLE);
            ViewGroup.LayoutParams layoutParams = clipWrapper.getLayoutParams();
            layoutParams.height = LayoutHelper.WRAP_CONTENT;
            clipWrapper.setLayoutParams(layoutParams);
        } else {
            setVisibility(View.GONE);
        }
    }

    private int getMaxListHeight() {
        int itemCount = adapter.getItemCount();
        return (int) (AndroidUtilities.dp(48) * (itemCount <= 6 ? itemCount : 5.5f) + AndroidUtilities.dp(4) * 2);
    }

    public void setOnAccountSelected(Runnable onAccountSelected) {
        this.onAccountSelected = onAccountSelected;
    }

    public void setOnAccountLongClick(OnAccountLongClick onAccountLongClick) {
        this.onAccountLongClick = onAccountLongClick;
    }

    public void loadAccounts() {
        loadAccounts(null);
    }

    public void loadAccounts(BadgeDTO badgeOverride) {
        this.badgeOverride = badgeOverride;
        accounts.clear();
        accounts.addAll(AccountsUiHelper.activated());
        adapter.notifyDataSetChanged();
    }

    public void toggleExpand() {
        setExpanded(!expanded);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded == expanded) {
            return;
        }
        this.expanded = expanded;
        MessagesController.getGlobalMainSettings().edit().putBoolean("accountsShown", expanded).apply();
        if (expanded) {
            loadAccounts();
            setVisibility(View.VISIBLE);
        }
        if (expandAnimator != null) {
            expandAnimator.cancel();
        }
        int fromHeight = currentAnimatedHeight;
        if (fromHeight < 0) {
            fromHeight = clipWrapper.getLayoutParams().height;
            if (fromHeight < 0) {
                fromHeight = clipWrapper.getHeight();
            }
            if (fromHeight < 0) {
                fromHeight = expanded ? 0 : clipWrapper.getMeasuredHeight();
            }
        }
        currentAnimatedHeight = -1;
        clipWrapper.measure(
            MeasureSpec.makeMeasureSpec(((View) getParent()).getMeasuredWidth() - AndroidUtilities.dp(24), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(getMaxListHeight(), MeasureSpec.AT_MOST)
        );
        int toHeight = expanded ? clipWrapper.getMeasuredHeight() : 0;
        currentAnimatedHeight = fromHeight;

        expandAnimator = ValueAnimator.ofInt(fromHeight, toHeight);
        expandAnimator.setDuration(250);
        expandAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        expandAnimator.addUpdateListener(animation -> {
            currentAnimatedHeight = (Integer) animation.getAnimatedValue();
            clipWrapper.requestLayout();
        });
        expandAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled || expandAnimator != animation) {
                    return;
                }
                expandAnimator = null;
                currentAnimatedHeight = -1;
                if (!DrawerAccountPickerView.this.expanded) {
                    setVisibility(View.GONE);
                    return;
                }
                ViewGroup.LayoutParams layoutParams = clipWrapper.getLayoutParams();
                layoutParams.height = LayoutHelper.WRAP_CONTENT;
                clipWrapper.setLayoutParams(layoutParams);
            }
        });
        expandAnimator.start();
    }

    public void updateColors() {
        bgPaint.setColor(Theme.getColor(COLOR_KEY_BACKGROUND));
        invalidate();
        adapter.notifyDataSetChanged();
    }

    public void updateUnreadCounters() {
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            if (child instanceof AccountRowView) {
                ((AccountRowView) child).updateUnreadCounter();
            }
        }
    }

    public void dispose() {
        if (expandAnimator != null) {
            expandAnimator.cancel();
            expandAnimator = null;
        }
        draggingItemView = null;
        recyclerView.setChildDrawingOrderCallback(null);
        recyclerView.stopScroll();
    }

    private boolean canAddAccount() {
        return AccountsUiHelper.freeSlotWithinLimit() != null;
    }

    private static Drawable createAccountItemRippleDrawable() {
        return Theme.createRadSelectorDrawable(Theme.getColor(COLOR_KEY_SELECTOR), 12, 12);
    }

    private static Drawable createSelectedAccountBackgroundDrawable() {
        return Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(COLOR_KEY_SURFACE), Theme.getColor(COLOR_KEY_SELECTOR));
    }

    public class AccountAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private AccountAdapter() {
        }

        @Override
        public int getItemViewType(int position) {
            return position < accounts.size() ? VIEW_TYPE_ACCOUNT : VIEW_TYPE_ADD_ACCOUNT;
        }

        @Override
        public int getItemCount() {
            return accounts.size() + (canAddAccount() ? 1 : 0);
        }

        public void swapElements(int fromIndex, int toIndex) {
            if (fromIndex < 0 || toIndex < 0 || fromIndex >= accounts.size() || toIndex >= accounts.size()) {
                return;
            }
            UserConfig fromConfig = UserConfig.getInstance(accounts.get(fromIndex));
            UserConfig toConfig = UserConfig.getInstance(accounts.get(toIndex));
            int loginTime = fromConfig.loginTime;
            fromConfig.loginTime = toConfig.loginTime;
            toConfig.loginTime = loginTime;
            fromConfig.saveConfig(false);
            toConfig.saveConfig(false);
            Collections.swap(accounts, fromIndex, toIndex);
            notifyItemMoved(fromIndex, toIndex);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = viewType == VIEW_TYPE_ADD_ACCOUNT ? new AddAccountView(parent.getContext()) : new AccountRowView(parent.getContext());
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int viewType = getItemViewType(position);
            if (viewType == VIEW_TYPE_ACCOUNT) {
                bindAccountViewHolder(holder, position);
            } else if (viewType == VIEW_TYPE_ADD_ACCOUNT) {
                bindAddAccountViewHolder(holder);
            }
        }

        private void bindAccountViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (!(holder.itemView instanceof AccountRowView)) {
                return;
            }
            AccountRowView rowView = (AccountRowView) holder.itemView;
            int account = accounts.get(position);
            rowView.bind(account, account == UserConfig.selectedAccount ? badgeOverride : null);
            rowView.setOnClickListener(v -> {
                if (account != UserConfig.selectedAccount) {
                    if (onAccountSelected != null) {
                        onAccountSelected.run();
                    }
                    AccountsUiHelper.switchTo(account);
                }
            });
            rowView.setOnLongClickListener(v -> {
                if (account == UserConfig.selectedAccount) {
                    itemTouchHelper.startDrag(holder);
                    return true;
                }
                if (onAccountLongClick != null) {
                    onAccountLongClick.onLongClick(account, v);
                }
                return true;
            });
        }

        private void bindAddAccountViewHolder(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof AddAccountView) {
                ((AddAccountView) holder.itemView).updateColors();
            }
            holder.itemView.setOnClickListener(v -> {
                if (onAccountSelected != null) {
                    onAccountSelected.run();
                }
                AndroidUtilities.runOnUIThread(AccountsUiHelper::add, 150);
            });
        }
    }

    public static class AccountRowView extends FrameLayout {

        private final AvatarDrawable avatarDrawable;
        private final BackupImageView avatarView;
        private final SimpleTextView nameView;
        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable premiumStatusDrawable;
        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable exteraBadgeDrawable;
        private final DrawerAccountUnreadBadge unreadBadge;
        private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF avatarRect = new RectF();
        private boolean selected;

        public AccountRowView(Context context) {
            super(context);
            setWillNotDraw(false);
            setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(44)));
            setBackground(createAccountItemRippleDrawable());

            avatarDrawable = new AvatarDrawable();
            avatarDrawable.setTextSize(AndroidUtilities.dp(20));

            avatarView = new BackupImageView(context);
            updateAvatarRadius();
            addView(avatarView, LayoutHelper.createFrame(34, 34, Gravity.LEFT | Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

            nameView = new SimpleTextView(context);
            nameView.setTextSize(15);
            nameView.setTypeface(AndroidUtilities.bold());
            nameView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
            nameView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            nameView.setEllipsizeByGradient(true);
            nameView.setCanHideRightDrawable(false);
            nameView.setRightDrawableOutside(true);
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, 54, 0, 12, 0));

            premiumStatusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameView, AndroidUtilities.dp(18));
            exteraBadgeDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameView, AndroidUtilities.dp(18));
            unreadBadge = new DrawerAccountUnreadBadge();

            checkPaint.setStyle(Paint.Style.STROKE);
            checkPaint.setStrokeWidth(AndroidUtilities.dp(1.67f));
            checkPaint.setStrokeCap(Paint.Cap.ROUND);
            checkPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        public void bind(int account, BadgeDTO badge) {
            TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
            if (user == null) {
                return;
            }
            updateAvatarRadius();
            avatarDrawable.setInfo(account, user);
            nameView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
            nameView.setText(ContactsController.formatName(user.first_name, user.last_name));
            avatarView.getImageReceiver().setCurrentAccount(account);
            avatarView.setForUserOrChat(user, avatarDrawable);
            premiumStatusDrawable.setCurrentAccount(account);
            exteraBadgeDrawable.setCurrentAccount(account);

            int statusColor = Theme.getColor(COLOR_KEY_STATUS);
            long emojiStatusId = DialogObject.getEmojiStatusDocumentId(user.emoji_status);
            boolean isPremium = MessagesController.getInstance(account).isPremiumUser(user);
            if (badge == null) {
                badge = BadgesController.INSTANCE.getBadge(user);
            }

            Drawable firstDrawable;
            Drawable secondDrawable = null;
            if (emojiStatusId != 0) {
                premiumStatusDrawable.set(emojiStatusId, false);
                firstDrawable = premiumStatusDrawable;
            } else if (isPremium) {
                premiumStatusDrawable.set(PremiumGradient.getInstance().premiumStarDrawableMini, false);
                firstDrawable = premiumStatusDrawable;
            } else {
                premiumStatusDrawable.set((Drawable) null, false);
                firstDrawable = null;
            }
            premiumStatusDrawable.setColor(statusColor);
            premiumStatusDrawable.setParticles(DialogObject.isEmojiStatusCollectible(user.emoji_status), false);

            Drawable badgeDrawable = updateBadgeDrawable(badge, statusColor);
            if (badgeDrawable != null) {
                if (firstDrawable != null) {
                    secondDrawable = badgeDrawable;
                } else {
                    firstDrawable = badgeDrawable;
                }
            }
            applyNameDrawables(firstDrawable, secondDrawable);
            unreadBadge.bind(account, nameView);

            checkPaint.setColor(Theme.getColor(COLOR_KEY_ACCENT));
            selected = account == UserConfig.selectedAccount;
            float scale = selected ? 0.785f : 1f;
            avatarView.setScaleX(scale);
            avatarView.setScaleY(scale);
            setBackground(selected ? createSelectedAccountBackgroundDrawable() : createAccountItemRippleDrawable());
            setPadding(0, 0, 0, 0);
            invalidate();
        }

        private void updateAvatarRadius() {
            avatarView.setRoundRadius(ExteraConfig.getAvatarCorners(34));
        }

        public void updateUnreadCounter() {
            unreadBadge.update(nameView);
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            premiumStatusDrawable.attach();
            exteraBadgeDrawable.attach();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            premiumStatusDrawable.detach();
            exteraBadgeDrawable.detach();
        }

        private Drawable updateBadgeDrawable(BadgeDTO badge, int color) {
            if (badge == null) {
                clearBadgeDrawables();
                return null;
            }
            exteraBadgeDrawable.set(badge.getDocumentId(), false);
            exteraBadgeDrawable.setParticles(true, false);
            exteraBadgeDrawable.setColor(color);
            return exteraBadgeDrawable;
        }

        private void clearBadgeDrawables() {
            exteraBadgeDrawable.set((Drawable) null, false);
            exteraBadgeDrawable.setParticles(false, false);
            exteraBadgeDrawable.setColor(null);
        }

        private void applyNameDrawables(Drawable rightDrawable, Drawable rightDrawable2) {
            if (rightDrawable != null && rightDrawable == nameView.getRightDrawable2()) {
                nameView.setRightDrawable2(null);
            }
            nameView.setRightDrawable(rightDrawable);
            nameView.setRightDrawable2(rightDrawable2);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            unreadBadge.draw(this, canvas);
            if (selected) {
                float halfStroke = checkPaint.getStrokeWidth() / 2f;
                avatarRect.set(avatarView.getLeft() + halfStroke, avatarView.getTop() + halfStroke, avatarView.getRight() - halfStroke, avatarView.getBottom() - halfStroke);
                float radius = ExteraConfig.getAvatarCorners(34);
                canvas.drawRoundRect(avatarRect, radius, radius, checkPaint);
            }
        }
    }

    public static class AddAccountView extends LinearLayout {

        private final Drawable circleDrawable;
        private final Drawable plusDrawable;
        private final SimpleTextView textView;

        public AddAccountView(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(44)));

            ImageView imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            circleDrawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.poll_add_circle, null);
            plusDrawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.poll_add_plus, null);
            if (circleDrawable != null) {
                circleDrawable.mutate();
            }
            if (plusDrawable != null) {
                plusDrawable.mutate();
            }
            CombinedDrawable combinedDrawable = new CombinedDrawable(circleDrawable, plusDrawable) {
                @Override
                public void setColorFilter(ColorFilter colorFilter) {

                }
            };
            combinedDrawable.setCustomSize(AndroidUtilities.dp(24), AndroidUtilities.dp(24));
            imageView.setImageDrawable(combinedDrawable);
            addView(imageView, LayoutHelper.createLinear(34, 34, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

            textView = new SimpleTextView(context);
            textView.setTextSize(15);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            textView.setText(LocaleController.getString(R.string.AddAccount));
            addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 12, 0, 12, 0));

            updateColors();
        }

        public void updateColors() {
            setBackground(createAccountItemRippleDrawable());
            if (circleDrawable != null) {
                circleDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(COLOR_KEY_ACCENT), PorterDuff.Mode.SRC_IN));
            }
            if (plusDrawable != null) {
                plusDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(COLOR_KEY_ADD_ICON), PorterDuff.Mode.SRC_IN));
            }
            textView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        }
    }
}
