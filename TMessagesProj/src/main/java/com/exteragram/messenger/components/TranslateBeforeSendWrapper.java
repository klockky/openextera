package com.exteragram.messenger.components;

import android.annotation.SuppressLint;
import android.content.Context;

import com.exteragram.messenger.translator.TranslatorUtils;
import com.exteragram.messenger.utils.ui.PopupUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;

@SuppressLint("ViewConstructor")
public abstract class TranslateBeforeSendWrapper extends ActionBarMenuSubItem {

    public TranslateBeforeSendWrapper(Context context, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        super(context, top, bottom, resourcesProvider);
        setTextAndIcon(LocaleController.getString(R.string.TranslateTo), R.drawable.msg_translate);
        setSubtext(TranslatorUtils.getSendTargetLanguageTitle());
        setMinimumWidth(AndroidUtilities.dp(196));
        setItemHeight(56);
        setOnClickListener(v -> onClick());
        setOnLongClickListener(v -> showDialog(context));
        setRightIcon(R.drawable.msg_arrowright);
        getRightIcon().setOnClickListener(v -> showDialog(context));
    }

    public abstract void onClick();

    private boolean showDialog(Context context) {
        CharSequence[] titles = TranslatorUtils.getTargetLanguageTitles();
        CharSequence[] items = new CharSequence[titles.length];
        System.arraycopy(titles, 0, items, 0, titles.length);
        PopupUtils.showDialog(items, LocaleController.getString(R.string.Language), TranslatorUtils.getSendTargetLanguageIndex(), context, i -> {
            TranslatorUtils.setSendTargetLanguage(TranslatorUtils.getTargetLanguageCodeByIndex(i));
            setSubtext(TranslatorUtils.getSendTargetLanguageTitle());
        });
        return true;
    }
}
