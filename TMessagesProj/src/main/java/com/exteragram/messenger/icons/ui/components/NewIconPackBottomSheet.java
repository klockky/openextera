package com.exteragram.messenger.icons.ui.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.icons.IconPack;
import com.exteragram.messenger.icons.ui.IconPacksEditorActivity;
import com.exteragram.messenger.icons.ui.picker.IconPickerController;
import com.exteragram.messenger.utils.system.VibratorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineEditText;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.HashMap;
import java.util.UUID;

public class NewIconPackBottomSheet extends BottomSheet {

    private static final int MAX_NAME_LENGTH = 64;
    private static final String DEFAULT_VERSION = "1.0";

    private OutlineEditText authorField;
    private ButtonWithCounterView doneButton;
    private OutlineEditText nameField;
    private final IconPack packToEdit;
    private final BaseFragment parentFragment;
    private OutlineEditText versionField;

    public NewIconPackBottomSheet(BaseFragment fragment, Context context) {
        this(fragment, context, null);
    }

    public NewIconPackBottomSheet(BaseFragment fragment, Context context, IconPack packToEdit) {
        super(context, true);
        this.packToEdit = packToEdit;
        fixNavigationBar();
        waitingKeyboard = true;
        smoothKeyboardAnimationEnabled = true;
        parentFragment = fragment;
        setCustomView(createView(getContext()));
        setTitle(LocaleController.getString(packToEdit == null ? R.string.NewIconPack : R.string.EditIconPackInfo), true);
    }

    @SuppressLint("ClickableViewAccessibility")
    public View createView(Context context) {
        ScrollView scrollView = new ScrollView(context);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        linearLayout.setOnTouchListener((v, event) -> true);

        FrameLayout fieldsLayout = new FrameLayout(context);
        linearLayout.addView(fieldsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        nameField = new OutlineEditText(context);
        nameField.getEditText().setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        nameField.getEditText().setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) -> {
            int keep = MAX_NAME_LENGTH - (dest.length() - (dend - dstart));
            int length = end - start;
            if (keep < length) {
                VibratorUtils.vibrate();
                AndroidUtilities.shakeView(nameField);
            }
            if (keep <= 0) {
                return "";
            }
            if (keep >= length) {
                return null;
            }
            keep += start;
            if (Character.isHighSurrogate(source.charAt(keep - 1))) {
                --keep;
                if (keep == start) {
                    return "";
                }
            }
            return source.subSequence(start, keep);
        }});
        nameField.getEditText().setImeOptions(EditorInfo.IME_ACTION_NEXT);
        nameField.setHint(LocaleController.getString(R.string.PackName));
        if (packToEdit != null) {
            nameField.getEditText().setText(packToEdit.getName());
        }
        fieldsLayout.addView(nameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
        nameField.getEditText().setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_NEXT) {
                return false;
            }
            authorField.requestFocus();
            authorField.getEditText().setSelection(authorField.getEditText().length());
            return true;
        });

        authorField = new OutlineEditText(context);
        authorField.setBackground(null);
        authorField.getEditText().setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        authorField.getEditText().setImeOptions(EditorInfo.IME_ACTION_NEXT);
        authorField.setHint(LocaleController.getString(R.string.AuthorNameOptional));
        if (packToEdit != null) {
            authorField.getEditText().setText(packToEdit.getAuthor());
        }
        fieldsLayout.addView(authorField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP, 0, 68, 0, 0));
        authorField.getEditText().setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_NEXT) {
                return false;
            }
            versionField.requestFocus();
            versionField.getEditText().setSelection(versionField.getEditText().length());
            return true;
        });

        versionField = new OutlineEditText(context);
        versionField.setBackground(null);
        versionField.getEditText().setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        versionField.getEditText().setImeOptions(EditorInfo.IME_ACTION_DONE);
        versionField.setHint(LocaleController.getString(R.string.Version));
        if (packToEdit != null) {
            versionField.getEditText().setText(packToEdit.getVersion());
        } else {
            versionField.getEditText().setText(DEFAULT_VERSION);
        }
        fieldsLayout.addView(versionField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP, 0, 136, 0, 0));
        versionField.getEditText().setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) {
                return false;
            }
            doneButton.callOnClick();
            return true;
        });

        doneButton = new ButtonWithCounterView(context, resourcesProvider);
        doneButton.setRound();
        doneButton.setText(LocaleController.getString(packToEdit == null ? R.string.Create : R.string.Save), false);
        doneButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        doneButton.setOnClickListener(v -> doOnDone());
        linearLayout.addView(doneButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 0, 16));

        return scrollView;
    }

    private void doOnDone() {
        if (nameField.getEditText().length() == 0) {
            VibratorUtils.vibrate();
            AndroidUtilities.shakeView(nameField);
            return;
        }
        final String name = nameField.getEditText().getText().toString().trim();
        final String author = authorField.getEditText().getText().toString().trim();
        String versionText = versionField.getEditText().getText().toString().trim();
        final String version = versionText.isEmpty() ? DEFAULT_VERSION : versionText;
        if (name.isEmpty()) {
            BulletinFactory.of(topBulletinContainer, resourcesProvider).createErrorBulletin(LocaleController.getString(R.string.NameCannotBeEmpty)).show();
            return;
        }
        Utilities.globalQueue.postRunnable(() -> savePack(name, author, version));
    }

    private void savePack(String name, String author, String version) {
        String authorName = author.isEmpty() ? LocaleController.getString(R.string.PluginNoAuthor) : author;
        if (packToEdit != null) {
            IconPack edited = new IconPack(packToEdit.getId(), name, authorName, version, packToEdit.getIcons(), packToEdit.getPreinstalledMap(), null);
            if (!IconManager.INSTANCE.saveIconPackMetadata(edited)) {
                showStorageError();
            } else {
                AndroidUtilities.runOnUIThread(this::dismiss);
            }
            return;
        }
        final String id = "custom." + UUID.randomUUID();
        final IconPack iconPack = new IconPack(id, name, authorName, version, new HashMap<>(), null, null);
        if (!IconManager.INSTANCE.saveIconPackMetadata(iconPack)) {
            showStorageError();
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            IconManager.INSTANCE.setActiveCustomPack(id);
            dismiss();
            ExteraConfig.setEditingIconPackId(iconPack.getId());
            parentFragment.presentFragment(new IconPacksEditorActivity(iconPack) {
                @Override
                public void onBecomeFullyVisible() {
                    if (LaunchActivity.instance != null) {
                        IconPickerController.setActive(LaunchActivity.instance, true);
                    }
                    super.onBecomeFullyVisible();
                }
            });
        });
    }

    private void showStorageError() {
        AndroidUtilities.runOnUIThread(() ->
            BulletinFactory.of(topBulletinContainer, resourcesProvider).createErrorBulletin(LocaleController.getString(R.string.IconPackErrorStorage)).show()
        );
    }

    @Override
    public void onOpenAnimationEnd() {
        super.onOpenAnimationEnd();
        if (nameField == null || nameField.getEditText() == null) {
            return;
        }
        nameField.getEditText().requestFocus();
        nameField.getEditText().setSelection(nameField.getEditText().length());
        AndroidUtilities.showKeyboard(nameField.getEditText());
    }
}
