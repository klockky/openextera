package com.exteragram.messenger.preferences.appearance.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.components.AltSeekbar;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;
import com.exteragram.messenger.preferences.components.PreviewBackgroundDrawable;
import com.exteragram.messenger.preferences.components.PreviewColors;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.ProfileChannelCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;
import java.util.Objects;

@SuppressLint("ViewConstructor")
public class AvatarCornersPreviewCell extends FrameLayout implements CustomPreferenceCell {

    private static final long EXTERAGRAM_CHANNEL_ID = 1571726392L;
    private static final int MAX_CORNERS = 28;

    public enum Mode {
        REAL,
        MOCK
    }

    private final Mode currentMode;
    private final FrameLayout preview;
    private final AltSeekbar seekBar;

    private DialogCell dialogCell;
    private ProfileChannelCell.ChannelMessageFetcher fetcher;
    private LoadingDrawable loadingDrawable;
    private AnimatedFloat channelLoadingAlpha;
    private AnimatedFloat messagesLoadingAlpha;
    private Paint mockPaint;
    private Paint brightMockPaint;

    private long currentDialogId;
    private boolean loadingChannel;
    private boolean loadingMessages;
    private boolean set;
    private int lastWidth;

    public AvatarCornersPreviewCell(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, int mode) {
        super(context);
        currentMode = Mode.values()[mode];
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        int account = UserConfig.selectedAccount;
        MessagesController messagesController = MessagesController.getInstance(account);

        preview = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                if (currentMode == Mode.REAL) {
                    drawLoading(canvas);
                } else {
                    drawMock(canvas);
                }
            }
        };
        if (currentMode == Mode.REAL) {
            ScaleStateListAnimator.apply(preview, 0.03f, 1.5f);
            preview.setOnClickListener(v -> messagesController.openByUserName("exteraGram", fragment, 1));
        }
        preview.setWillNotDraw(false);
        preview.setBackground(new PreviewBackgroundDrawable());

        seekBar = new AltSeekbar(context, progress -> {
            ExteraConfig.setAvatarCorners(progress);
            invalidate();
            preview.invalidate();
            if (dialogCell != null) {
                dialogCell.update(0);
            }
            fragment.getParentLayout().rebuildFragments(0);
        }, 0, MAX_CORNERS, LocaleController.getString(R.string.AvatarCorners), LocaleController.getString(R.string.AvatarCornersLeft), LocaleController.getString(R.string.AvatarCornersRight));
        seekBar.setProgress(ExteraConfig.getAvatarCorners());
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (currentMode == Mode.REAL) {
            initRealMode(context, resourcesProvider, account, messagesController);
            preview.addView(dialogCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 3, 0, 4));
        } else {
            initMockMode();
            preview.setMinimumHeight(AndroidUtilities.dp(83));
        }
        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 21, 114, 21, 21));
    }

    private void initRealMode(Context context, Theme.ResourcesProvider resourcesProvider, int account, MessagesController messagesController) {
        fetcher = new ProfileChannelCell.ChannelMessageFetcher(account);
        channelLoadingAlpha = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        messagesLoadingAlpha = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        loadingDrawable = new LoadingDrawable();
        loadingDrawable.setColors(
                Theme.multAlpha(Theme.getColor(Theme.key_listSelector), 1.3f),
                Theme.multAlpha(Theme.getColor(Theme.key_listSelector), 0.85f)
        );
        loadingDrawable.setRadiiDp(8);

        dialogCell = new DialogCell(null, context, false, true, account, resourcesProvider);
        dialogCell.isForChannelSubscriberCell = true;
        dialogCell.setDialogCellDelegate(new DialogCell.DialogCellDelegate() {
            @Override
            public void onButtonClicked(DialogCell dialogCell) {
            }

            @Override
            public void onButtonLongPress(DialogCell dialogCell) {
            }

            @Override
            public boolean canClickButtonInside() {
                return false;
            }

            @Override
            public void openStory(DialogCell dialogCell, Runnable onDone) {
            }

            @Override
            public void showChatPreview(DialogCell dialogCell) {
            }

            @Override
            public void openHiddenStories() {
            }
        });
        dialogCell.avatarStart = 15;
        dialogCell.messagePaddingStart = 83;

        if (messagesController.getChat(EXTERAGRAM_CHANNEL_ID) != null) {
            setDialogId(EXTERAGRAM_CHANNEL_ID);
            return;
        }
        loadingChannel = true;
        loadingMessages = true;
        messagesController.getUserNameResolver().resolve("exteraGram", peerId -> {
            loadingChannel = false;
            if (peerId != null) {
                setDialogId(-peerId);
            }
            invalidate();
        });
    }

    private void initMockMode() {
        mockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mockPaint.setColor(PreviewColors.getMockColor(false));
        brightMockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        brightMockPaint.setColor(PreviewColors.getMockColor(true));
    }

    private void drawMock(Canvas canvas) {
        float top = AndroidUtilities.dp(1);
        float avatarStart = AndroidUtilities.dp(15);
        float messageStart = AndroidUtilities.dp(83);
        int avatarSize = AndroidUtilities.dp(56);
        float width = preview.getWidth();
        RectF rect = AndroidUtilities.rectTmp;

        float avatarTop = AndroidUtilities.dp(12) + top;
        float avatarRight = avatarStart + avatarSize;
        float avatarBottom = avatarTop + avatarSize;
        rect.set(avatarStart, avatarTop, avatarRight, avatarBottom);

        float roundness = Math.max(0f, Math.min(1f, 1f - ExteraConfig.getAvatarCorners() / MAX_CORNERS));
        float outerRadius = AndroidUtilities.dpf2(7f + 2f * roundness);
        float innerRadius = AndroidUtilities.dpf2(5f + roundness);
        float dotX = avatarRight - ExteraConfig.getOnlineDotOffset(AndroidUtilities.dpf2(8f), outerRadius);
        float dotY = avatarBottom - ExteraConfig.getOnlineDotOffset(AndroidUtilities.dpf2(7.5f), outerRadius);

        canvas.save();
        Path clipPath = new Path();
        clipPath.addCircle(dotX, dotY, outerRadius, Path.Direction.CCW);
        canvas.clipPath(clipPath, Region.Op.DIFFERENCE);
        canvas.drawRoundRect(rect, ExteraConfig.getAvatarCorners(56f), ExteraConfig.getAvatarCorners(56f), brightMockPaint);
        canvas.restore();

        Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
        canvas.drawCircle(dotX, dotY, innerRadius, Theme.dialogs_onlineCirclePaint);

        float radius = AndroidUtilities.dp(4);
        float textStart = messageStart + AndroidUtilities.dp(6);
        rect.set(textStart, AndroidUtilities.dp(16) + top, textStart + width * 0.4f, AndroidUtilities.dp(24.33f) + top);
        canvas.drawRoundRect(rect, radius, radius, brightMockPaint);
        rect.set(textStart, AndroidUtilities.dp(38) + top, textStart + width * 0.5f, AndroidUtilities.dp(46.33f) + top);
        canvas.drawRoundRect(rect, radius, radius, mockPaint);
        rect.set(textStart, AndroidUtilities.dp(56) + top, textStart + width * 0.36f, AndroidUtilities.dp(64.33f) + top);
        canvas.drawRoundRect(rect, radius, radius, mockPaint);
        rect.set(width - AndroidUtilities.dp(16) - AndroidUtilities.dp(43), AndroidUtilities.dp(16) + top, width - AndroidUtilities.dp(16), AndroidUtilities.dp(24.33f) + top);
        canvas.drawRoundRect(rect, radius, radius, mockPaint);
    }

    private void drawLoading(Canvas canvas) {
        if (loadingDrawable == null) {
            return;
        }
        float channelAlpha = channelLoadingAlpha.set(loadingChannel);
        float messagesAlpha = messagesLoadingAlpha.set(loadingMessages);
        boolean needInvalidate = false;
        RectF rect = AndroidUtilities.rectTmp;
        float cellX = dialogCell.getX();
        float cellY = dialogCell.getY();
        float textStart = cellX + AndroidUtilities.dp(dialogCell.messagePaddingStart + 6);

        if (channelAlpha > 0) {
            loadingDrawable.setAlpha((int) (channelAlpha * 255));
            int avatarSize = AndroidUtilities.dp(56);
            float x = cellX + AndroidUtilities.dp(dialogCell.avatarStart);
            float y = cellY + AndroidUtilities.dp(12);
            loadingDrawable.setRadiiDp((int) ExteraConfig.getAvatarCorners());
            rect.set(x, y, x + avatarSize, y + avatarSize);
            loadingDrawable.setBounds(rect);
            loadingDrawable.draw(canvas);

            loadingDrawable.setRadiiDp(4);
            rect.set(textStart, cellY + AndroidUtilities.dp(16), textStart + getWidth() * 0.4f, cellY + AndroidUtilities.dp(24.33f));
            loadingDrawable.setBounds(rect);
            loadingDrawable.draw(canvas);
            needInvalidate = true;
        }

        if (messagesAlpha > 0) {
            loadingDrawable.setAlpha((int) (messagesAlpha * 255));
            rect.set(textStart, cellY + AndroidUtilities.dp(38), textStart + getWidth() * 0.5f, cellY + AndroidUtilities.dp(46.33f));
            loadingDrawable.setBounds(rect);
            loadingDrawable.draw(canvas);

            rect.set(textStart, cellY + AndroidUtilities.dp(56), textStart + getWidth() * 0.36f, cellY + AndroidUtilities.dp(64.33f));
            loadingDrawable.setBounds(rect);
            loadingDrawable.draw(canvas);

            float cellRight = cellX + dialogCell.getWidth();
            rect.set(cellRight - AndroidUtilities.dp(16) - AndroidUtilities.dp(43), cellY + AndroidUtilities.dp(12), cellRight - AndroidUtilities.dp(16), cellY + AndroidUtilities.dp(20.33f));
            loadingDrawable.setBounds(rect);
            loadingDrawable.draw(canvas);
            needInvalidate = true;
        }

        if (needInvalidate) {
            invalidate();
        }
    }

    public void setDialogId(long dialogId) {
        if (currentMode != Mode.REAL) {
            return;
        }
        boolean firstSet = !set;
        currentDialogId = dialogId;
        messagesLoadingAlpha.set(1f, true);
        fetcher.fetch(dialogId, 0);
        fetcher.subscribe(() -> {
            if (dialogCell != null && currentDialogId == dialogId) {
                ArrayList<MessageObject> messages = fetcher.messageObjects;
                if (messages == null || messages.isEmpty()) {
                    if (!firstSet) {
                        dialogCell.setDialog(-dialogId, null, 0, false, true);
                    }
                    loadingMessages = true;
                } else {
                    MessageObject lastMessage = messages.get(messages.size() - 1);
                    dialogCell.setDialog(-dialogId, lastMessage, messages, lastMessage.messageOwner.date, false, !firstSet);
                    loadingMessages = false;
                }
                dialogCell.invalidate();
            }
            if (!firstSet) {
                messagesLoadingAlpha.set(loadingMessages, true);
            }
            invalidate();
            set = true;
        });
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return loadingDrawable == who || super.verifyDrawable(who);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        preview.invalidate();
        seekBar.invalidate();
        if (dialogCell != null) {
            dialogCell.invalidate();
        }
        lastWidth = -1;
    }

    public void updateSliderStyle() {
        seekBar.updateStyle();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        setMeasuredDimension(width, getMeasuredHeight());
        if (lastWidth != width) {
            lastWidth = width;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof AvatarCornersPreviewCell) {
            return Objects.equals(seekBar, ((AvatarCornersPreviewCell) o).seekBar);
        }
        return false;
    }
}
