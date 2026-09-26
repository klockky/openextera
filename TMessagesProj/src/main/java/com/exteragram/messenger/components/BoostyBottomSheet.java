package com.exteragram.messenger.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.api.dto.BoostySubscriberDTO;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.List;

public abstract class BoostyBottomSheet extends BottomSheet {

    private final int ITEM_HEIGHT = AndroidUtilities.dp(48);
    private final int PRIMARY_COLOR = 0xFFF3542E;

    private final RecyclerListView listView;
    private final LinearSnapHelper snapHelper = new LinearSnapHelper();
    private final StarParticlesView.Drawable starDrawable;

    public FrameLayout topView;
    public TextView titleView;
    public LinkSpanDrawable.LinksTextView descriptionView;
    public ButtonWithCounterView buttonView;

    private int contentHeight;
    private int currentAutoScrollPosition;
    private boolean isUserScrolling;
    private Runnable resumeScrollRunnable;

    private final Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (listView == null || isUserScrolling || listView.getAdapter() == null) {
                return;
            }
            if (listView.getAdapter().getItemCount() <= 1) {
                return;
            }
            LinearLayoutManager layoutManager = (LinearLayoutManager) listView.getLayoutManager();
            if (layoutManager == null) {
                return;
            }
            currentAutoScrollPosition++;
            final boolean wrapped;
            if (currentAutoScrollPosition >= listView.getAdapter().getItemCount()) {
                currentAutoScrollPosition = 0;
                wrapped = true;
            } else {
                wrapped = false;
            }
            LinearSmoothScroller smoothScroller = new LinearSmoothScroller(listView.getContext()) {
                @Override
                protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                    return super.calculateSpeedPerPixel(displayMetrics) * (wrapped ? 0.6f : 12.5f);
                }

                @Override
                public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                    return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2);
                }
            };
            smoothScroller.setTargetPosition(currentAutoScrollPosition);
            layoutManager.startSmoothScroll(smoothScroller);
            listView.removeCallbacks(this);
            listView.postDelayed(this, wrapped ? 3000 : 1500);
        }
    };

    public BoostyBottomSheet(Context context, List<BoostySubscriberDTO> subscribers) {
        super(context, false);
        int backgroundColor = Theme.isCurrentThemeDark() ? 0xFF181818 : 0xFFF5F5F5;
        setBackgroundColor(backgroundColor);
        fixNavigationBar(backgroundColor);
        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        useBackgroundTopPadding = false;

        Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerPaint.setColor(PRIMARY_COLOR);

        starDrawable = new StarParticlesView.Drawable(300);
        starDrawable.color = 0x60FFFFFF;
        starDrawable.size1 = 8;
        starDrawable.size2 = 6;
        starDrawable.size3 = 4;
        starDrawable.k1 = starDrawable.k2 = starDrawable.k3 = 0.98f;
        starDrawable.useRotate = true;
        starDrawable.speedScale = 2f;
        starDrawable.checkBounds = true;
        starDrawable.checkTime = true;
        starDrawable.useBlur = true;
        starDrawable.roundEffect = false;
        starDrawable.init();

        FrameLayout headerView = new FrameLayout(context) {
            private final Path path = new Path();
            private final RectF rectF = new RectF();

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (AndroidUtilities.isTablet()) {
                    contentHeight = (int) (MeasureSpec.getSize(heightMeasureSpec) * 0.45f);
                } else if (isPortrait) {
                    contentHeight = (int) (MeasureSpec.getSize(widthMeasureSpec) * 0.8f);
                } else {
                    contentHeight = (int) (MeasureSpec.getSize(heightMeasureSpec) * 0.65f);
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(contentHeight + AndroidUtilities.dp(2), MeasureSpec.EXACTLY));
                rectF.set(0, AndroidUtilities.dp(2), getMeasuredWidth(), getMeasuredHeight() + AndroidUtilities.dp(18));
                starDrawable.rect.set(0, AndroidUtilities.dp(2), getMeasuredWidth(), getMeasuredHeight());
                starDrawable.rect2.set(-AndroidUtilities.dp(15), -AndroidUtilities.dp(15), getMeasuredWidth() + AndroidUtilities.dp(15), getMeasuredHeight() + AndroidUtilities.dp(15));
                starDrawable.resetPositions();
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                float radius = AndroidUtilities.dp(12) - 1;
                rectF.set(0, AndroidUtilities.dp(2), getMeasuredWidth(), getMeasuredHeight() + AndroidUtilities.dp(18));
                headerPaint.setShader(new RadialGradient(rectF.centerX(), rectF.centerY() - AndroidUtilities.dp(9), Math.max(rectF.width(), rectF.height()) / 2f, PRIMARY_COLOR, 0xFFD94B29, Shader.TileMode.CLAMP));
                canvas.save();
                canvas.clipRect(0, AndroidUtilities.dp(2), getMeasuredWidth(), getMeasuredHeight());
                canvas.drawRoundRect(rectF, radius, radius, headerPaint);
                starDrawable.onDraw(canvas);
                invalidate();
                canvas.restore();

                path.reset();
                path.addRoundRect(rectF, radius, radius, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(path);
                super.dispatchDraw(canvas);
                canvas.restore();
            }
        };

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        topView = new FrameLayout(context);
        headerView.addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 2, 0, 0));

        listView = new RecyclerListView(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int height = MeasureSpec.getSize(heightSpec);
                if (height > 0) {
                    int padding = (height - ITEM_HEIGHT) / 2;
                    if (getPaddingTop() != padding) {
                        setPadding(0, padding, 0, padding);
                    }
                }
                super.onMeasure(widthSpec, heightSpec);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return super.onInterceptTouchEvent(e);
            }

            @Override
            @SuppressLint("ClickableViewAccessibility")
            public boolean onTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                boolean result = super.onTouchEvent(e);
                if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return result || e.getAction() == MotionEvent.ACTION_DOWN;
            }
        };
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new RecyclerView.Adapter<ViewHolder>() {
            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextView textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
                textView.setGravity(Gravity.CENTER);
                textView.setTextColor(0xFFFFFFFF);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setMaxLines(2);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
                textView.setMinHeight(ITEM_HEIGHT);
                textView.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new ViewHolder(textView);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                holder.textView.setText(subscribers.get(position).getName());
            }

            @Override
            public int getItemCount() {
                return subscribers.size();
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateScales();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    isUserScrolling = true;
                    listView.removeCallbacks(autoScrollRunnable);
                    if (resumeScrollRunnable != null) {
                        listView.removeCallbacks(resumeScrollRunnable);
                    }
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE && isUserScrolling) {
                    LinearLayoutManager layoutManager = (LinearLayoutManager) listView.getLayoutManager();
                    if (layoutManager != null) {
                        View snapView = snapHelper.findSnapView(layoutManager);
                        if (snapView != null) {
                            currentAutoScrollPosition = layoutManager.getPosition(snapView);
                        } else {
                            currentAutoScrollPosition = layoutManager.findFirstVisibleItemPosition();
                        }
                    }
                    resumeScrollRunnable = () -> {
                        isUserScrolling = false;
                        listView.postDelayed(autoScrollRunnable, 1500);
                    };
                    listView.postDelayed(resumeScrollRunnable, 3000);
                }
            }
        });
        listView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateScales());
        listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.scrollToPosition(0);
        listView.postDelayed(autoScrollRunnable, 1500);
        snapHelper.attachToRecyclerView(listView);
        topView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        linearLayout.addView(headerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        int textColor = Theme.isCurrentThemeDark() ? 0xFFFFFFFF : 0xFF000000;

        titleView = new TextView(context);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setTextColor(textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(LocaleController.formatPluralString("BoostyPeopleCount", subscribers.size()));
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 24, 20, 0));

        descriptionView = new LinkSpanDrawable.LinksTextView(context);
        descriptionView.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        descriptionView.setTextColor(textColor);
        descriptionView.setText(AndroidUtilities.replaceTags(new SpannableStringBuilder(LocaleController.getString(R.string.BoostyInfo))
                .append("\n")
                .append(LocaleController.getString(R.string.BoostyInfo2))));
        linearLayout.addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 6, 20, 0));

        buttonView = new ButtonWithCounterView(context, resourcesProvider);
        buttonView.setRound();
        SpannableStringBuilder buttonText = new SpannableStringBuilder(LocaleController.getString(R.string.Open)).append("  Boosty");
        ColoredImageSpan boostyIcon = new ColoredImageSpan(R.drawable.boosty_icon, ColoredImageSpan.ALIGN_CENTER);
        boostyIcon.setSize(AndroidUtilities.dp(20));
        buttonText.setSpan(boostyIcon, buttonText.length() - 7, buttonText.length() - 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        buttonView.setText(buttonText, false);
        buttonView.setTextColor(0xFFFFFFFF);
        buttonView.setOnClickListener(v -> {
            dismiss();
            onButtonClick();
        });
        buttonView.setColor(PRIMARY_COLOR, ColorUtils.setAlphaComponent(ColorUtils.blendARGB(PRIMARY_COLOR, 0, 0.2f), 51));
        linearLayout.addView(buttonView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 14, 24, 14, 14));

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(linearLayout);
        setCustomView(scrollView);
    }

    public abstract void onButtonClick();

    @Override
    protected boolean isTouchOutside(float x, float y) {
        if (y < containerView.getTop() - backgroundPaddingTop || y > containerView.getBottom()) {
            return super.isTouchOutside(x, y);
        }
        return false;
    }

    private void updateScales() {
        if (listView == null) {
            return;
        }
        int center = listView.getHeight() / 2;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            float distance = Math.min(Math.abs(center - (child.getTop() + child.getBottom()) / 2) / (listView.getHeight() / 2f), 1f);
            float scale = 1f - distance * 0.3f;
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha((1f - distance) * 0.7f + 0.3f);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        public ViewHolder(View view) {
            super(view);
            textView = (TextView) view;
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (listView != null) {
            listView.removeCallbacks(autoScrollRunnable);
            if (resumeScrollRunnable != null) {
                listView.removeCallbacks(resumeScrollRunnable);
            }
        }
    }
}
