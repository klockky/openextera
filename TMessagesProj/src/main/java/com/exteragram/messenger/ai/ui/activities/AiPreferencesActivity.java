package com.exteragram.messenger.ai.ui.activities;

import android.text.TextUtils;
import android.view.View;

import com.exteragram.messenger.ai.AiConfig;
import com.exteragram.messenger.ai.AiController;
import com.exteragram.messenger.preferences.BasePreferencesActivity;
import com.exteragram.messenger.preferences.utils.SettingsRegistry;
import com.exteragram.messenger.utils.text.LocaleUtils;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class AiPreferencesActivity extends BasePreferencesActivity {

    public enum PreferenceItem {
        ENDPOINT,
        ROLE,
        HISTORY,
        TEMPERATURE,
        RESPONSE_STREAMING,
        SHOW_RESPONSE_ONLY,
        INSERT_AS_QUOTE,
        REPLACE_TELEGRAM_EDITOR,
        REPLACE_TELEGRAM_SUMMARIES;

        public int getId() {
            return ordinal() + 1;
        }
    }

    @Override
    public boolean needHideTitle() {
        return true;
    }

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.AIChat);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView(getTitle(), LocaleController.getString(R.string.AIChatInfo2), "exteraGramPlaceholders", "🤖"));
        items.add(UItem.asShadow());

        items.add(UItem.asHeader(LocaleController.getString(R.string.General)));
        items.add(UItem.asButton(PreferenceItem.ENDPOINT.getId(), R.drawable.msg_language, LocaleController.getString(R.string.Services), getEndpointValue())
                .prioritizeTitleOverValue(true).setSearchable(this).setLinkAlias("aiServices", this));
        items.add(UItem.asButton(PreferenceItem.ROLE.getId(), R.drawable.msg_openprofile, LocaleController.getString(R.string.Roles), AiConfig.getSelectedRole())
                .prioritizeTitleOverValue(true).setSearchable(this).setLinkAlias("aiRoles", this));
        items.add(UItem.asButton(PreferenceItem.HISTORY.getId(), R.drawable.msg_discuss, LocaleController.getString(R.string.MessageHistory), getHistoryValue())
                .prioritizeTitleOverValue(true).setSearchable(this).setLinkAlias("aiHistory", this));
        items.add(UItem.asShadow(LocaleController.getString(R.string.HistoryInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.AIReplaceTelegram)));
        items.add(UItem.asCheck(PreferenceItem.REPLACE_TELEGRAM_EDITOR.getId(), LocaleController.getString(R.string.AIFeaturesEditor), LocaleController.getString(R.string.AIReplaceTelegramEditorInfo), true)
                .setChecked(AiConfig.getReplaceTelegramEditor()).setSearchable(this).setLinkAlias("replaceTelegramEditor", this));
        items.add(UItem.asCheck(PreferenceItem.REPLACE_TELEGRAM_SUMMARIES.getId(), LocaleController.getString(R.string.AIFeaturesSummaries), LocaleController.getString(R.string.AIReplaceTelegramSummariesInfo), true)
                .setChecked(AiConfig.getReplaceTelegramSummaries()).showDivider(false).setSearchable(this).setLinkAlias("replaceTelegramSummaries", this));
        items.add(UItem.asShadow(LocaleController.getString(R.string.AIReplaceTelegramInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.AIGeneration)));
        items.add(UItem.asCheck(PreferenceItem.RESPONSE_STREAMING.getId(), LocaleController.getString(R.string.ResponseStreaming), LocaleController.getString(R.string.ResponseStreamingInfo), true)
                .setChecked(AiConfig.getResponseStreaming()).setSearchable(this).setLinkAlias("responseStreaming", this));
        items.add(UItem.asCheck(PreferenceItem.SHOW_RESPONSE_ONLY.getId(), LocaleController.getString(R.string.ShowResponseOnly))
                .setChecked(AiConfig.getShowResponseOnly()).setSearchable(this).setLinkAlias("showResponseOnly", this));
        items.add(UItem.asCheck(PreferenceItem.INSERT_AS_QUOTE.getId(), LocaleController.getString(R.string.InsertResponseAsQuote))
                .setChecked(AiConfig.getInsertAsQuote()).showDivider(false).setSearchable(this).setLinkAlias("insertResponseAsQuote", this));
        items.add(UItem.asShadow());

        String temperatureTitle = LocaleController.getString(R.string.AITemperature);
        items.add(UItem.asHeader(SettingsRegistry.markAsNewFeature("aiTemperature") ? LocaleUtils.applyNewSpan(temperatureTitle) : temperatureTitle));
        items.add(createTemperatureSliderItem().showDivider(false).setSearchable(this).setLinkAlias("aiTemperature", this));
        items.add(UItem.asShadow(LocaleController.getString(R.string.AITemperatureInfo)));
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id <= 0 || item.id > PreferenceItem.values().length) {
            return;
        }
        switch (PreferenceItem.values()[item.id - 1]) {
            case ENDPOINT:
                presentFragment(new ServicesActivity());
                break;
            case ROLE:
                presentFragment(new RolesActivity());
                break;
            case HISTORY:
                presentFragment(new AiHistoryActivity());
                break;
            case RESPONSE_STREAMING:
                toggleBooleanSettingAndRefresh(item, AiConfig::setResponseStreaming);
                break;
            case SHOW_RESPONSE_ONLY:
                toggleBooleanSettingAndRefresh(item, AiConfig::setShowResponseOnly);
                break;
            case INSERT_AS_QUOTE:
                toggleBooleanSettingAndRefresh(item, AiConfig::setInsertAsQuote);
                break;
            case REPLACE_TELEGRAM_EDITOR:
                toggleBooleanSettingAndRefresh(item, AiConfig::setReplaceTelegramEditor);
                break;
            case REPLACE_TELEGRAM_SUMMARIES:
                toggleBooleanSettingAndRefresh(item, AiConfig::setReplaceTelegramSummaries);
                break;
        }
    }

    private CharSequence getHistoryValue() {
        if (!AiConfig.getSaveHistory()) {
            return LocaleController.getString(R.string.BlurOff);
        }
        int size = AiConfig.getConversationHistory().size();
        if (size == 0) {
            return LocaleController.getString(R.string.BlockedEmpty);
        }
        return LocaleController.formatPluralString("messages", size);
    }

    private UItem createTemperatureSliderItem() {
        UItem item = UItem.asIntSlideView(1, 0, AiConfig.getTemperature(), 20, this::formatTemperature, AiConfig::setTemperature);
        item.id = PreferenceItem.TEMPERATURE.getId();
        item.text = LocaleController.getString(R.string.AITemperature);
        return item;
    }

    private CharSequence formatTemperature(int value) {
        return String.format(Locale.US, "%.1f", value / 10.0f);
    }

    private String getEndpointValue() {
        if (AiController.getInstance().getSelected().isOnDevice()) {
            return LocaleController.getString(AiController.canUseAI() ? R.string.AIOnDevice : R.string.BlockedEmpty);
        }
        try {
            String host = new URL(AiController.getInstance().getSelected().getUrl()).getHost();
            if (!TextUtils.isEmpty(host) && AiController.canUseAI()) {
                return host.contains("generativelanguage.googleapis") ? "Gemini" : host;
            }
            return LocaleController.getString(R.string.BlockedEmpty);
        } catch (MalformedURLException e) {
            return LocaleController.getString(R.string.BlockedEmpty);
        }
    }
}
