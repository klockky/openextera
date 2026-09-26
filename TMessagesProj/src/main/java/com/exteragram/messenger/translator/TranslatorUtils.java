package com.exteragram.messenger.translator;

import android.text.TextUtils;
import android.text.style.URLSpan;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.translator.core.BaseTranslator;
import com.exteragram.messenger.translator.core.TranslationError;
import com.exteragram.messenger.translator.providers.GoogleTranslator;
import com.exteragram.messenger.translator.providers.TelegramTranslator;
import com.exteragram.messenger.utils.chats.ChatUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.TranslateAlert2;
import org.telegram.ui.RestrictedLanguagesSelectActivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class TranslatorUtils {

    public static final String TARGET_LANG_APP = "app";

    private static final int MAX_RECENT_SEND_LANGUAGES = 3;

    private static final String[] DEVICE_MODELS = {
            "Galaxy S6", "Galaxy S7", "Galaxy S8", "Galaxy S9", "Galaxy S10", "Galaxy S21",
            "Pixel 3", "Pixel 4", "Pixel 5",
            "OnePlus 6", "OnePlus 7", "OnePlus 8", "OnePlus 9",
            "Xperia XZ", "Xperia XZ2", "Xperia XZ3", "Xperia 1", "Xperia 5", "Xperia 10", "Xperia L4"
    };
    private static final String[] CHROME_VERSIONS = {
            "111.0.5563.57", "94.0.4606.81", "80.0.3987.119", "69.0.3497.100", "92.0.4515.159", "71.0.3578.99"
    };

    private static final LinkedHashMap<String, String> detectedLanguages = new LinkedHashMap<String, String>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 200;
        }
    };

    private static List<TranslateController.Language> getLanguages() {
        return new ArrayList<>(TranslateController.getLanguages());
    }

    private static List<TranslateController.Language> getIndexedTargetLanguages() {
        return getCurrentTargetLanguages();
    }

    public static String normalizeLanguageCode(String code) {
        if (TextUtils.isEmpty(code)) {
            return null;
        }
        String normalized = code.trim().toLowerCase(Locale.US).replace('_', '-');
        return "nb".equals(normalized) ? "no" : normalized;
    }

    public static String primaryLanguageOf(String code) {
        String normalized = normalizeLanguageCode(code);
        if (TextUtils.isEmpty(normalized)) {
            return null;
        }
        int index = normalized.indexOf('-');
        return index >= 0 ? normalized.substring(0, index) : normalized;
    }

    public static boolean isTargetLanguageFollowApp() {
        return TARGET_LANG_APP.equalsIgnoreCase(ExteraConfig.getTargetLang());
    }

    public static boolean isRestrictedLanguage(String code) {
        if (TextUtils.isEmpty(code)) {
            return false;
        }
        if (TextUtils.equals(primaryLanguageOf(code), primaryLanguageOf(getResolvedTargetLanguageCode()))) {
            return true;
        }
        String primary = primaryLanguageOf(code);
        if (TextUtils.isEmpty(primary)) {
            return false;
        }
        for (String restricted : RestrictedLanguagesSelectActivity.getRestrictedLanguages()) {
            if (TextUtils.equals(primary, primaryLanguageOf(restricted))) {
                return true;
            }
        }
        return false;
    }

    private static TranslateController.Language createLanguageItem(String code) {
        String normalized = normalizeLanguageCode(code);
        TranslateController.Language language = new TranslateController.Language();
        language.code = normalized;
        Locale currentLocale = LocaleController.getInstance().getCurrentLocale();
        if (currentLocale == null) {
            currentLocale = Locale.getDefault();
        }
        Locale locale = Locale.forLanguageTag(normalized == null ? "" : normalized);
        String displayName = locale.getDisplayName(currentLocale);
        String ownDisplayName = locale.getDisplayName(locale);
        if (TextUtils.isEmpty(displayName)) {
            displayName = TranslateAlert2.capitalFirst(TranslateAlert2.languageName(normalized));
        }
        if (TextUtils.isEmpty(ownDisplayName)) {
            ownDisplayName = TranslateAlert2.capitalFirst(TranslateAlert2.systemLanguageName(normalized, true));
        }
        if (TextUtils.isEmpty(displayName)) {
            displayName = normalized != null ? normalized.toUpperCase(Locale.US) : "";
        }
        if (TextUtils.isEmpty(ownDisplayName)) {
            ownDisplayName = displayName;
        }
        language.displayName = TranslateAlert2.capitalFirst(displayName);
        language.ownDisplayName = TranslateAlert2.capitalFirst(ownDisplayName);
        language.q = (language.displayName + " " + language.ownDisplayName).toLowerCase(Locale.US);
        return language;
    }

    public static ArrayList<TranslateController.Language> getCurrentTargetLanguages() {
        BaseTranslator translator = getCurrentTranslator();
        Set<String> supportedLanguages = translator.getSupportedLanguages();
        if (supportedLanguages == null || supportedLanguages.isEmpty()) {
            ArrayList<TranslateController.Language> languages = new ArrayList<>(TranslateController.getLanguages());
            if (translator == TelegramTranslator.getInstance()) {
                languages.removeIf(language -> language == null || TextUtils.isEmpty(language.code) || language.code.contains("-") || language.code.contains("_"));
            }
            return languages;
        }
        HashSet<String> codes = new HashSet<>();
        for (String code : supportedLanguages) {
            String normalized = normalizeLanguageCode(code);
            if (!TextUtils.isEmpty(normalized)) {
                codes.add(normalized);
            }
        }
        ArrayList<TranslateController.Language> languages = new ArrayList<>();
        for (String code : codes) {
            languages.add(createLanguageItem(code));
        }
        languages.sort(Comparator.comparing(language -> language.displayName == null ? "" : language.displayName, String::compareToIgnoreCase));
        return languages;
    }

    private static String getResolvedAppLanguageCode() {
        Locale currentLocale = LocaleController.getInstance().getCurrentLocale();
        if (currentLocale == null) {
            currentLocale = Locale.getDefault();
        }
        String language = normalizeLanguageCode(currentLocale.getLanguage());
        String country = currentLocale.getCountry();
        if (!TextUtils.isEmpty(language) && !TextUtils.isEmpty(country)) {
            String withCountry = normalizeLanguageCode(language + "-" + country);
            if (isTargetLanguageSupportedForCurrentProvider(withCountry)) {
                return withCountry;
            }
        }
        if (!isTargetLanguageSupportedForCurrentProvider(language)) {
            String appLanguage = normalizeLanguageCode(LocaleController.getString(R.string.LanguageCode));
            if (isTargetLanguageSupportedForCurrentProvider(appLanguage)) {
                return appLanguage;
            }
        }
        return language;
    }

    public static String getResolvedTargetLanguageCode(String code) {
        if (TARGET_LANG_APP.equalsIgnoreCase(code)) {
            return getResolvedAppLanguageCode();
        }
        String normalized = normalizeLanguageCode(code);
        return TextUtils.isEmpty(normalized) ? getResolvedAppLanguageCode() : normalized;
    }

    public static String getResolvedTargetLanguageCode() {
        return getResolvedTargetLanguageCode(ExteraConfig.getTargetLang());
    }

    public static CharSequence[] getTargetLanguageTitles() {
        List<TranslateController.Language> languages = getIndexedTargetLanguages();
        CharSequence[] titles = new CharSequence[languages.size() + 1];
        titles[0] = LocaleController.getString(R.string.TranslationTargetApp);
        for (int i = 0; i < languages.size(); i++) {
            TranslateController.Language language = languages.get(i);
            titles[i + 1] = language.displayName + (language.ownDisplayName == null ? "" : " – " + language.ownDisplayName);
        }
        return titles;
    }

    public static int getTargetLanguageIndexByCode(String code) {
        if (TARGET_LANG_APP.equalsIgnoreCase(code)) {
            return 0;
        }
        List<TranslateController.Language> languages = getIndexedTargetLanguages();
        String resolved = getResolvedTargetLanguageCode(code);
        for (int i = 0; i < languages.size(); i++) {
            if (TextUtils.equals(languages.get(i).code, resolved)) {
                return i + 1;
            }
        }
        return 0;
    }

    public static String getTargetLanguageCodeByIndex(int index) {
        if (index == 0) {
            return TARGET_LANG_APP;
        }
        List<TranslateController.Language> languages = getIndexedTargetLanguages();
        int position = index - 1;
        if (position < 0 || position >= languages.size()) {
            return null;
        }
        return languages.get(position).code;
    }

    public static String getTargetLanguageTitle() {
        if (isTargetLanguageFollowApp()) {
            return LocaleController.getString(R.string.TranslationTargetApp);
        }
        return getLanguageTitleSystem(getResolvedTargetLanguageCode());
    }

    private static String getStoredSendTargetLanguage() {
        return ExteraConfig.getPreferences().getString("targetLangSend", null);
    }

    private static void storeRecentSendTargetLanguage(String code) {
        String normalized = normalizeLanguageCode(code);
        if (TextUtils.isEmpty(normalized)) {
            return;
        }
        ArrayList<String> recent = getRecentSendTargetLanguages();
        recent.removeIf(item -> TextUtils.equals(item, normalized));
        recent.add(0, normalized);
        while (recent.size() > MAX_RECENT_SEND_LANGUAGES) {
            recent.remove(recent.size() - 1);
        }
        ExteraConfig.getEditor().putString("targetLangSendRecent", TextUtils.join(",", recent)).apply();
    }

    public static ArrayList<String> getRecentSendTargetLanguages() {
        ArrayList<String> result = new ArrayList<>();
        String stored = ExteraConfig.getPreferences().getString("targetLangSendRecent", null);
        if (!TextUtils.isEmpty(stored)) {
            HashSet<String> seen = new HashSet<>();
            for (String code : stored.split(",")) {
                String normalized = normalizeLanguageCode(code);
                if (!TextUtils.isEmpty(normalized) && !seen.contains(normalized) && isTargetLanguageSupportedForCurrentProvider(normalized)) {
                    seen.add(normalized);
                    result.add(normalized);
                    if (result.size() >= MAX_RECENT_SEND_LANGUAGES) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    public static String getResolvedSendTargetLanguageCode() {
        String stored = getStoredSendTargetLanguage();
        if (TextUtils.isEmpty(stored)) {
            return "en";
        }
        return getResolvedTargetLanguageCode(stored);
    }

    public static int getSendTargetLanguageIndex() {
        String stored = getStoredSendTargetLanguage();
        if (TextUtils.isEmpty(stored)) {
            stored = "en";
        }
        return getTargetLanguageIndexByCode(stored);
    }

    public static String getSendTargetLanguageTitle() {
        return getLanguageTitleSystem(getResolvedSendTargetLanguageCode());
    }

    public static boolean isSendTargetLanguageFollowApp() {
        return TARGET_LANG_APP.equalsIgnoreCase(getStoredSendTargetLanguage());
    }

    public static void setSendTargetLanguage(String code) {
        String normalized = TARGET_LANG_APP.equalsIgnoreCase(code) ? TARGET_LANG_APP : normalizeLanguageCode(code);
        if (TextUtils.isEmpty(normalized)) {
            ExteraConfig.getEditor().remove("targetLangSend").apply();
            return;
        }
        if (!TextUtils.equals(normalized, TARGET_LANG_APP)) {
            storeRecentSendTargetLanguage(normalized);
        }
        if (TextUtils.equals(normalized, "en")) {
            ExteraConfig.getEditor().remove("targetLangSend").apply();
        } else {
            ExteraConfig.getEditor().putString("targetLangSend", normalized).apply();
        }
    }

    public static void setTargetLanguage(String code) {
        String normalized = TARGET_LANG_APP.equalsIgnoreCase(code) ? TARGET_LANG_APP : normalizeLanguageCode(code);
        String value = TextUtils.isEmpty(normalized) ? TARGET_LANG_APP : normalized;
        ExteraConfig.setTargetLang(value);
        ExteraConfig.getEditor().putString("targetLang", value).apply();
        if (MessagesController.getGlobalMainSettings().getBoolean("translate_button_restricted_languages_changed", false)) {
            return;
        }
        MessagesController.getGlobalMainSettings().edit().remove("translate_button_restricted_languages").apply();
        RestrictedLanguagesSelectActivity.invalidateRestrictedLanguages();
        RestrictedLanguagesSelectActivity.checkRestrictedLanguages(false);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            try {
                MessagesController.getInstance(a).getTranslateController().checkRestrictedLanguagesUpdate();
            } catch (Exception ignore) {
            }
        }
    }

    public static boolean isTargetLanguageSupportedForCurrentProvider(String code) {
        if (TARGET_LANG_APP.equalsIgnoreCase(code)) {
            return true;
        }
        String normalized = normalizeLanguageCode(code);
        if (TextUtils.isEmpty(normalized)) {
            return false;
        }
        BaseTranslator translator = getCurrentTranslator();
        Set<String> supportedLanguages = translator.getSupportedLanguages();
        if (supportedLanguages == null || supportedLanguages.isEmpty()) {
            return translator != TelegramTranslator.getInstance() || !normalized.contains("-");
        }
        for (String supported : supportedLanguages) {
            if (TextUtils.equals(normalizeLanguageCode(supported), normalized)) {
                return true;
            }
        }
        return false;
    }

    public static void ensureTargetLanguageCompatibleWithProvider() {
        if (!isTargetLanguageSupportedForCurrentProvider(ExteraConfig.getTargetLang())) {
            setTargetLanguage(TARGET_LANG_APP);
        }
    }

    public static String formatUserAgent() {
        String androidVersion = String.valueOf(Utilities.random.nextInt(7) + 6);
        String device = DEVICE_MODELS[Utilities.random.nextInt(DEVICE_MODELS.length)];
        String chrome = CHROME_VERSIONS[Utilities.random.nextInt(CHROME_VERSIONS.length)];
        return String.format("Mozilla/5.0 (Linux; Android %s; %s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%s Mobile Safari/537.36", androidVersion, device, chrome);
    }

    public static String getLanguageTitleSystem(String code) {
        if ("none".equals(code)) {
            return LocaleController.getString(R.string.None);
        }
        return getLanguages().stream()
                .filter(language -> TextUtils.equals(language.code, code))
                .findFirst()
                .map(language -> language.displayName)
                .orElseGet(() -> {
                    String normalized = normalizeLanguageCode(code);
                    return TextUtils.isEmpty(normalized) ? "" : normalized.toUpperCase(Locale.US);
                });
    }

    public static String getLanguageDisplayName(String code) {
        return getLanguages().stream()
                .filter(language -> TextUtils.equals(language.code, code))
                .findFirst()
                .map(language -> language.ownDisplayName)
                .orElse(null);
    }

    public static String getDetectedLanguage(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        if (messageObject.messageOwner != null && !TextUtils.isEmpty(messageObject.messageOwner.originalLanguage)) {
            return messageObject.messageOwner.originalLanguage;
        }
        return detectedLanguages.get(detectedLanguageKey(messageObject));
    }

    public static boolean isMessageLanguageRestricted(MessageObject messageObject) {
        String language = getDetectedLanguage(messageObject);
        return language != null && !TranslateController.UNKNOWN_LANGUAGE.equals(language) && isRestrictedLanguage(language);
    }

    public static void detectMessageLanguage(MessageObject messageObject, MessageObject.GroupedMessages group, Utilities.Callback<String> callback) {
        if (messageObject == null) {
            if (callback != null) {
                callback.run(null);
            }
            return;
        }
        String detected = getDetectedLanguage(messageObject);
        if (detected != null) {
            if (callback != null) {
                callback.run(detected);
            }
            return;
        }
        CharSequence text = ChatUtils.getInstance().getMessageText(messageObject, group);
        if (!TextUtils.isEmpty(text) && LanguageDetector.hasSupport()) {
            String key = detectedLanguageKey(messageObject);
            LanguageDetector.detectLanguage(text.toString(), language -> AndroidUtilities.runOnUIThread(() -> {
                String result = TextUtils.isEmpty(language) ? TranslateController.UNKNOWN_LANGUAGE : language;
                detectedLanguages.put(key, result);
                if (callback != null) {
                    callback.run(result);
                }
            }), e -> AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) {
                    callback.run(null);
                }
            }));
        } else if (callback != null) {
            callback.run(null);
        }
    }

    private static String detectedLanguageKey(MessageObject messageObject) {
        return messageObject.getDialogId() + ":" + messageObject.getId();
    }

    public static void translateWithAlert(MessageObject messageObject, MessageObject.GroupedMessages group, TLRPC.InputPeer peer, int msgId, BaseFragment fragment) {
        if (messageObject == null || fragment == null || fragment.getContext() == null) {
            return;
        }
        ChatActivity chatActivity = fragment instanceof ChatActivity ? (ChatActivity) fragment : null;
        Utilities.CallbackReturn<URLSpan, Boolean> onLinkPress = link -> {
            if (chatActivity != null) {
                chatActivity.didPressMessageUrl(link, false, messageObject, null);
            }
            return true;
        };
        ArrayList<TLRPC.MessageEntity> entities = messageObject.messageOwner != null ? messageObject.messageOwner.entities : null;
        CharSequence text = ChatUtils.getInstance().getMessageText(messageObject, group);
        detectMessageLanguage(messageObject, group, language -> {
            if (fragment.getContext() == null) {
                return;
            }
            String fromLanguage = TranslateController.UNKNOWN_LANGUAGE.equals(language) ? null : language;
            TranslateAlert2.showAlert(fragment.getContext(), fragment, UserConfig.selectedAccount, peer, msgId, false, fromLanguage, TranslateAlert2.getToLanguage(), text, entities, false, onLinkPress, () -> {
                if (chatActivity != null) {
                    chatActivity.dimBehindView(false);
                }
            });
        });
    }

    public static void translateWithDefault(CharSequence text, TLRPC.InputPeer peer, int msgId, String toLang, ArrayList<TLRPC.MessageEntity> entities, TranslateCallback callback) {
        String resolvedLang = getResolvedTargetLanguageCode(toLang);
        TLRPC.TL_messages_translateText req = new TLRPC.TL_messages_translateText();
        TLRPC.TL_textWithEntities source = new TLRPC.TL_textWithEntities();
        source.text = text == null ? "" : text.toString();
        if (entities != null) {
            source.entities = entities;
        }
        if (peer != null) {
            req.flags |= 1;
            req.peer = peer;
            req.id.add(msgId);
        } else {
            req.flags |= 2;
            req.text.add(source);
        }
        String lang = resolvedLang != null ? resolvedLang.trim() : null;
        if ("nb".equals(lang)) {
            lang = "no";
        }
        req.to_lang = lang;
        int reqId = ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {
            if (error != null && "TRANSLATIONS_DISABLED_ALT".equalsIgnoreCase(error.text)) {
                GoogleTranslator.getInstance().translate(text.toString(), "auto", resolvedLang, new TranslateCallback() {
                    @Override
                    public void onSuccess(String result) {
                        if (TextUtils.isEmpty(result)) {
                            AndroidUtilities.runOnUIThread(callback::onFailed);
                        } else {
                            callback.onSuccess(result);
                            TLRPC.TL_textWithEntities textWithEntities = new TLRPC.TL_textWithEntities();
                            textWithEntities.text = result;
                            callback.onSuccess(textWithEntities);
                        }
                    }

                    @Override
                    public void onSuccess(TLObject response, TLRPC.TL_error error) {
                        callback.onSuccess(response, error);
                    }

                    @Override
                    public void onFailed() {
                        callback.onFailed();
                    }

                    @Override
                    public void onReqId(int reqId) {
                        callback.onReqId(reqId);
                    }
                });
                return;
            }
            if (response instanceof TLRPC.TL_messages_translateResult) {
                TLRPC.TL_messages_translateResult result = (TLRPC.TL_messages_translateResult) response;
                if (!result.result.isEmpty() && result.result.get(0) != null && result.result.get(0).text != null) {
                    TLRPC.TL_textWithEntities received = result.result.get(0);
                    TLRPC.TL_textWithEntities processed = TranslateAlert2.preprocess(source, received);
                    String translated = processed != null && processed.text != null ? processed.text : received.text;
                    if (TextUtils.isEmpty(translated)) {
                        AndroidUtilities.runOnUIThread(callback::onFailed);
                    } else {
                        AndroidUtilities.runOnUIThread(() -> {
                            callback.onSuccess(result, error);
                            callback.onSuccess(processed != null ? processed : received);
                            callback.onSuccess(translated);
                        });
                    }
                    return;
                }
            }
            AndroidUtilities.runOnUIThread(callback::onFailed);
        });
        callback.onReqId(reqId);
    }

    public interface TranslateCallback {
        void onFailed();

        default void onReqId(int reqId) {
        }

        default void onSuccess(String translated) {
        }

        default void onSuccess(TLObject response, TLRPC.TL_error error) {
        }

        default void onSuccess(TLRPC.TL_textWithEntities translated) {
        }

        default void onFailed(TranslationError error) {
            onFailed();
        }
    }

    public static void translate(CharSequence text, String toLang, ArrayList<TLRPC.MessageEntity> entities, TranslateCallback callback) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        String resolvedLang = getResolvedTargetLanguageCode(toLang);
        if (LanguageDetector.hasSupport()) {
            LanguageDetector.detectLanguage(text.toString(), fromLang -> {
                if (fromLang == null || fromLang.equals(TranslateController.UNKNOWN_LANGUAGE)) {
                    fromLang = "auto";
                }
                translate(text, fromLang, resolvedLang, entities, callback);
            }, e -> translate(text, "auto", resolvedLang, entities, callback));
        } else {
            translate(text, "auto", resolvedLang, entities, callback);
        }
    }

    public static void translate(CharSequence text, String fromLang, String toLang, ArrayList<TLRPC.MessageEntity> entities, TranslateCallback callback) {
        BaseTranslator translator = getCurrentTranslator();
        if (translator == TelegramTranslator.getInstance()) {
            translateWithDefault(text, null, 0, toLang, entities, callback);
            return;
        }
        if (!translator.isLanguageSupported(toLang)) {
            translator = GoogleTranslator.getInstance();
        }
        translator.translate(text.toString(), fromLang, toLang, new TranslateCallback() {
            @Override
            public void onSuccess(String result) {
                if (TextUtils.isEmpty(result)) {
                    callback.onFailed();
                    return;
                }
                callback.onSuccess(result);
                TLRPC.TL_textWithEntities textWithEntities = new TLRPC.TL_textWithEntities();
                textWithEntities.text = result;
                callback.onSuccess(textWithEntities);
            }

            @Override
            public void onSuccess(TLObject response, TLRPC.TL_error error) {
                callback.onSuccess(response, error);
            }

            @Override
            public void onFailed() {
                callback.onFailed();
            }

            @Override
            public void onFailed(TranslationError error) {
                callback.onFailed(error);
            }

            @Override
            public void onReqId(int reqId) {
                callback.onReqId(reqId);
            }
        });
    }

    public static BaseTranslator getCurrentTranslator() {
        return TranslationProviders.current();
    }

    public static boolean isAlternativeProvider() {
        return TranslationProviders.isAlternative();
    }

    public static String getCurrentTranslatorName() {
        return getCurrentTranslator().getDisplayName();
    }
}
