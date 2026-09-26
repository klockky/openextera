package com.exteragram.messenger.ai.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.exteragram.messenger.ai.AiConfig;
import com.exteragram.messenger.ai.AiController;
import com.exteragram.messenger.ai.network.backend.OnDeviceAvailability;
import com.exteragram.messenger.components.CheckBoxRow;
import com.exteragram.messenger.utils.chats.ChatUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.Objects;

import kotlin.Unit;

public class GenerateFromMessageBottomSheet extends BottomSheet {

    BaseFragment parentFragment;
    private final EditTextCell promptCell;
    private boolean useHistory;
    private boolean includeImage;

    public GenerateFromMessageBottomSheet(MessageObject messageObject, MessageObject.GroupedMessages group, BaseFragment fragment, Context context, Utilities.Callback<GenerationData> onGenerate) {
        this(Objects.toString(ChatUtils.getInstance().getMessageText(messageObject, group), ""), ChatUtils.getInstance().getPathToMessage(messageObject), fragment, context, onGenerate);
    }

    public GenerateFromMessageBottomSheet(BaseFragment fragment, Context context, Utilities.Callback<GenerationData> onGenerate, boolean allowHistory) {
        this("", "", fragment, context, onGenerate, allowHistory);
    }

    public GenerateFromMessageBottomSheet(String prompt, String imagePath, BaseFragment fragment, Context context, Utilities.Callback<GenerationData> onGenerate) {
        this(prompt, imagePath, fragment, context, onGenerate, AiConfig.getSaveHistory());
    }

    @SuppressLint("ClickableViewAccessibility")
    public GenerateFromMessageBottomSheet(String prompt, String imagePath, BaseFragment fragment, Context context, Utilities.Callback<GenerationData> onGenerate, boolean allowHistory) {
        super(context, true, fragment.getResourceProvider());
        setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        fixNavigationBar(getThemedColor(Theme.key_windowBackgroundGray));
        setApplyTopPadding(false);
        smoothKeyboardAnimationEnabled = true;
        parentFragment = fragment;
        useHistory = AiConfig.getSaveHistory();
        includeImage = AiController.canSendImage(imagePath);
        OnDeviceAvailability.warmup();

        ScrollView scrollView = new ScrollView(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setClipChildren(false);
        layout.setClipToPadding(false);
        scrollView.addView(layout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        FrameLayout header = new FrameLayout(context);
        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.Generate));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.LEFT | Gravity.TOP, 22, 6, 56, 0));

        ImageView closeView = new ImageView(context);
        closeView.setImageResource(R.drawable.ic_close_white);
        closeView.setScaleType(ImageView.ScaleType.CENTER);
        closeView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        ScaleStateListAnimator.apply(closeView);
        closeView.setOnClickListener(v -> dismiss());
        header.addView(closeView, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 10, 12, 0));
        layout.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 70));

        promptCell = new EditTextCell(context, LocaleController.getString(R.string.RolePrompt), true, false, -1, resourcesProvider);
        promptCell.editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        promptCell.editText.setMaxLines(8);
        promptCell.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(20), getThemedColor(Theme.key_windowBackgroundWhite)));
        if (!TextUtils.isEmpty(prompt)) {
            promptCell.setText(prompt);
        }
        promptCell.editText.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        layout.addView(promptCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 0));

        boolean showHistory = AiConfig.getSaveHistory() && allowHistory;
        boolean showImage = AiController.canSendImage(imagePath);
        LinearLayout optionsLayout = null;
        if (showHistory || showImage) {
            optionsLayout = new LinearLayout(context);
            optionsLayout.setOrientation(LinearLayout.HORIZONTAL);
            optionsLayout.setGravity(Gravity.CENTER_HORIZONTAL);
            if (showHistory) {
                CheckBoxRow historyRow = new CheckBoxRow(context, LocaleController.getString(R.string.GenerateWithHistory), useHistory, resourcesProvider);
                historyRow.setOnCheckedChange(checked -> {
                    useHistory = checked;
                    return Unit.INSTANCE;
                });
                optionsLayout.addView(historyRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 0));
            }
            if (showImage) {
                CheckBoxRow imageRow = new CheckBoxRow(context, LocaleController.getString(R.string.GenerateWithPhoto), includeImage, resourcesProvider);
                imageRow.setOnCheckedChange(checked -> {
                    includeImage = checked;
                    return Unit.INSTANCE;
                });
                BackupImageView imageView = new BackupImageView(context);
                imageView.getImageReceiver().setImage(imagePath, "24_24", null, null, 0);
                imageView.setRoundRadius(AndroidUtilities.dp(4));
                imageRow.addView(imageView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 9, 0, 0, 0));
                optionsLayout.addView(imageRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 0));
            }
        }

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider).setRound();
        button.setText(LocaleController.getString(R.string.Proceed), false);
        button.setOnClickListener(v -> {
            String text = promptCell.editText.getText().toString();
            if (TextUtils.isEmpty(text)) {
                if (!includeImage) {
                    AndroidUtilities.shakeViewSpring(promptCell);
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    return;
                }
                text = LocaleController.getString(R.string.AttachPhoto);
            }
            dismiss();
            onGenerate.run(new GenerationData(text, useHistory, includeImage ? imagePath : null));
        });
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 12, 12, optionsLayout != null ? 16 : 12));
        if (optionsLayout != null) {
            layout.addView(optionsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        }
        setCustomView(scrollView);
    }

    @Override
    public void show() {
        super.show();
        AndroidUtilities.runOnUIThread(() -> {
            promptCell.editText.setSelection(promptCell.editText.length());
            AndroidUtilities.showKeyboard(promptCell.editText);
        }, 200);
    }

    public static final class GenerationData {

        private final String prompt;
        private final boolean useHistory;
        private final String imagePath;

        public GenerationData(String prompt, boolean useHistory, String imagePath) {
            this.prompt = prompt;
            this.useHistory = useHistory;
            this.imagePath = imagePath;
        }

        public String prompt() {
            return prompt;
        }

        public boolean useHistory() {
            return useHistory;
        }

        public String imagePath() {
            return imagePath;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof GenerationData)) {
                return false;
            }
            GenerationData that = (GenerationData) o;
            return useHistory == that.useHistory && Objects.equals(prompt, that.prompt) && Objects.equals(imagePath, that.imagePath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(prompt, useHistory, imagePath);
        }

        @NonNull
        @Override
        public String toString() {
            return "GenerationData[prompt=" + prompt + ", useHistory=" + useHistory + ", imagePath=" + imagePath + "]";
        }
    }
}
