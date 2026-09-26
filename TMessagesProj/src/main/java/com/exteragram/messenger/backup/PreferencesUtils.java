package com.exteragram.messenger.backup;

import android.app.Activity;
import android.util.Log;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.collection.LongSparseArray;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.ai.AiConfig;
import com.exteragram.messenger.ai.network.backend.OnDeviceAvailability;
import com.exteragram.messenger.pillstack.core.PillStackConfig;
import com.exteragram.messenger.plugins.PluginsController;
import com.exteragram.messenger.translator.TranslationProviders;
import com.exteragram.messenger.utils.chats.ChatUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ShareAlert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PreferencesUtils {

    private static final String TAG = "OpenExteraBackup";

    private static PreferencesUtils instance;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final Set<String> excludedExteraKeys = Set.of(
            "pluginsUnknownSources",
            "editingIconPackId",
            "iconPacksLayout",
            "iconPacksHidden",
            "updateScheduleTimestamp",
            "sdkUpdateScheduleTimestamp",
            "selectedService",
            "selectedServiceId",
            "lastActivePillId",
            "customWeatherLocation",
            "customWeatherAddress",
            "debugCameraMetrics",
            "forceCompactSavedMusic",
            "disableApiRequests",
            "disableChatFadeWallpaperBlend",
            "chatFadeUseWhiteBackground"
    );

    private static final BackupItem[] extraExteraKeys = {
            new BackupItem("bottomNavigationBarMode", Integer.class),
            new BackupItem("mainMenuLayout", String.class),
            new BackupItem("mainMenuHiddenItems", String.class),
            new BackupItem("targetLangSend", String.class),
            new BackupItem("pluginsEngine", Boolean.class),
            new BackupItem("pinnedPlugins", Set.class),
            new BackupItem("hideStickerTime", Boolean.class),
            new BackupItem("springAnimations", Boolean.class),
            new BackupItem("aospTransitions", Boolean.class),
            new BackupItem("stickerCornerRoundness", Integer.class),
            new BackupItem("saveHistory", Boolean.class),
            new BackupItem("responseStreaming", Boolean.class),
            new BackupItem("temperature", Integer.class),
            new BackupItem("showResponseOnly", Boolean.class),
            new BackupItem("insertAsQuote", Boolean.class),
            new BackupItem("onDeviceReasoning", Boolean.class),
            new BackupItem("onDevicePreviewModel", Boolean.class),
            new BackupItem("onDeviceFastModel", Boolean.class),
            new BackupItem("replaceTelegramEditor", Boolean.class),
            new BackupItem("replaceTelegramSummaries", Boolean.class),
            new BackupItem("selectedRole", String.class),
            new BackupItem("infiniteScrolling", Boolean.class),
            new BackupItem("useCurrentLocation", Boolean.class),
            new BackupItem("gramTargetCurrency", String.class),
            new BackupItem("btcTargetCurrency", String.class),
            new BackupItem("usdTargetCurrency", String.class)
    };

    private static final BackupItem[] aiConfigKeys = {
            new BackupItem("roles", String.class)
    };

    private static final BackupItem[] pillStackConfigKeys = {
            new BackupItem("activePills", String.class),
            new BackupItem("hiddenPills", String.class)
    };

    private static final BackupItem[] mainConfigKeys = {
            new BackupItem("ChatSwipeAction", Integer.class),
            new BackupItem("mediaColumnsCount", Integer.class),
            new BackupItem("bubbleRadius", Integer.class),
            new BackupItem("fons_size", Integer.class)
    };

    private static final String[] configs = {"exteraconfig", "aiConfig", "pillstackconfig", "mainconfig"};

    public static PreferencesUtils getInstance() {
        if (instance == null) {
            instance = new PreferencesUtils();
        }
        return instance;
    }

    public static void clearPreferences() {
        AiConfig.getEditor().clear().apply();
        PillStackConfig.getEditor().clear().apply();
        ExteraConfig.getEditor().clear().apply();
        ExteraConfig.reloadConfig();
        PillStackConfig.reloadConfig();
    }

    private static Context getContext() {
        if (ApplicationLoader.applicationContext != null) {
            return ApplicationLoader.applicationContext;
        }
        return AndroidUtilities.getActivity();
    }

    public static SharedPreferences getPreferences(String name) {
        return getContext().getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    public static String generateBackupName(String name) {
        return (name != null ? name : "backup") + "-" + Utilities.generateRandomString(4) + ".extera";
    }

    private BackupItem findBackupItem(String config, String key) {
        if (TextUtils.isEmpty(config) || TextUtils.isEmpty(key)) {
            return null;
        }
        switch (config) {
            case "aiConfig":
                return findBackupItem(aiConfigKeys, key);
            case "pillstackconfig":
                return findBackupItem(pillStackConfigKeys, key);
            case "exteraconfig":
                BackupItem item = findBackupItem(extraExteraKeys, key);
                if (item != null) {
                    return item;
                }
                if (excludedExteraKeys.contains(key)) {
                    return null;
                }
                for (BackupItem backupItem : ExteraConfig.getBackupKeys()) {
                    if (backupItem.key.equals(key)) {
                        return backupItem;
                    }
                }
                return null;
            case "mainconfig":
                return findBackupItem(mainConfigKeys, key);
            default:
                return null;
        }
    }

    private BackupItem findBackupItem(BackupItem[] items, String key) {
        for (BackupItem item : items) {
            if (item.key.equals(key)) {
                return item;
            }
        }
        return null;
    }

    private boolean isExpectedValue(String config, String key, Object value) {
        BackupItem item = findBackupItem(config, key);
        if (item == null || value == null) {
            return false;
        }
        JsonElement element = value instanceof JsonElement ? (JsonElement) value : gson.toJsonTree(value);
        try {
            if (item.clazz.equals(Boolean.class)) {
                return element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean();
            } else if (item.clazz.equals(Float.class)) {
                return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() && isExpectedFloat(key, element.getAsFloat());
            } else if (item.clazz.equals(String.class)) {
                return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && isExpectedString(config, key, element.getAsString());
            } else if (item.clazz.equals(Set.class)) {
                return isExpectedStringSet(key, element);
            } else if (item.clazz.equals(Long.class)) {
                return false;
            } else {
                Integer intValue = getExactInteger(element);
                return intValue != null && isExpectedInteger(key, intValue);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private Integer getExactInteger(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            try {
                return new BigDecimal(element.getAsString()).intValueExact();
            } catch (ArithmeticException | NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Long getExactLong(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            try {
                return new BigDecimal(element.getAsString()).longValueExact();
            } catch (ArithmeticException | NumberFormatException ignored) {
            }
        }
        return null;
    }

    private boolean isExpectedFloat(String key, float value) {
        if (!Float.isFinite(value)) {
            return false;
        }
        switch (key) {
            case "sectionRadius":
            case "avatarCorners":
                return value >= 0 && value <= 28;
            case "flashIntensity":
            case "flashWarmth":
                return value >= 0 && value <= 1;
            case "stickerSize":
                return value >= 4 && value <= 20;
            case "predictiveBackIntensity":
                return value >= 0 && value <= 5;
            default:
                return false;
        }
    }

    private boolean isExpectedInteger(String key, int value) {
        switch (key) {
            case "titleText":
            case "stickerCornerRoundness":
            case "doubleTapSeekDuration":
                return value >= 0 && value <= 3;
            case "stickerShape":
            case "cameraType":
            case "downloadSpeedBoost":
            case "tabletMode":
            case "stickerTimeMode":
            case "videoMessagesCamera":
            case "transitionAnimation":
            case "dividerStyle":
            case "tabIcons":
            case "iconPack":
            case "glassOutlineStyle":
            case "bottomNavigationBarMode":
            case "bottomButton":
            case "showIdAndDc":
                return value >= 0 && value <= 2;
            case "bubbleRadius":
                return value >= 0 && value <= 17;
            case "ChatSwipeAction":
                return value >= 0 && value <= 5;
            case "mediaColumnsCount":
                return value >= 2 && value <= 9;
            case "fons_size":
                return value >= 12 && value <= 30;
            case "doubleTapAction":
                return value >= 0 && value <= 8;
            case "doNotUseProxy":
                return value >= 0 && value <= 7;
            case "eventType":
                return value >= 0 && value <= 4;
            case "temperature":
                return value >= 0 && value <= 20;
            case "doubleTapActionOutOwner":
                return value >= 0 && value <= 9;
            case "translationProvider":
                return value >= 0 && value <= TranslationProviders.getLastIndex();
            default:
                return false;
        }
    }

    private boolean isExpectedString(String config, String key, String value) {
        if (value == null || value.length() > 1024 * 1024) {
            return false;
        }
        switch (key) {
            case "selectedRole":
                return !TextUtils.isEmpty(value) && value.length() <= 256;
            case "swipeActions":
                return value.isEmpty() || value.matches("^\\d{1,2}(,\\d{1,2})*$");
            case "customSavePath":
                return value.matches("^(?!\\.{1,2}$)[A-Za-z0-9._ -]{1,255}$");
            case "mainMenuLayout":
                return isExpectedMainMenuLayout(value, true);
            case "targetLang":
            case "targetLangSend":
                return value.equalsIgnoreCase("app") || value.matches("^[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*$");
            case "mainMenuHiddenItems":
                return isExpectedMainMenuLayout(value, false);
            default:
                if ("aiConfig".equals(config) && key.equals("roles")) {
                    return isExpectedRoles(value);
                }
                if ("pillstackconfig".equals(config) && (key.equals("activePills") || key.equals("hiddenPills"))) {
                    return isExpectedPillsLayout(value);
                }
                if (key.equals("gramTargetCurrency") || key.equals("btcTargetCurrency") || key.equals("usdTargetCurrency")) {
                    return value.matches("^[A-Z]{3,5}$");
                }
                return !TextUtils.isEmpty(value);
        }
    }

    private boolean isExpectedStringSet(String key, JsonElement element) {
        if (!key.equals("pinnedPlugins") || !element.isJsonArray() || element.getAsJsonArray().size() > 1000) {
            return false;
        }
        HashSet<String> values = new HashSet<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                return false;
            }
            String value = item.getAsString();
            if (TextUtils.isEmpty(value) || value.length() > 255 || !values.add(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean isExpectedMainMenuLayout(String value, boolean allowDividers) {
        JsonElement element = gson.fromJson(value, JsonElement.class);
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() > 100) {
            return false;
        }
        HashSet<Integer> ids = new HashSet<>();
        for (JsonElement item : element.getAsJsonArray()) {
            Integer id = getExactInteger(item);
            if (id == null) {
                return false;
            }
            if (id == -1) {
                if (!allowDividers) {
                    return false;
                }
            } else if (!ids.add(id)) {
                return false;
            }
        }
        return true;
    }

    private boolean isExpectedRoles(String value) {
        JsonElement element = gson.fromJson(value, JsonElement.class);
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() > 100) {
            return false;
        }
        HashSet<String> names = new HashSet<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                return false;
            }
            JsonObject role = item.getAsJsonObject();
            if (!isRequiredString(role, "name", 256) || !isRequiredString(role, "prompt", 65536) || !names.add(role.get("name").getAsString())) {
                return false;
            }
            if (role.has("emojiId") && !role.get("emojiId").isJsonNull() && getExactLong(role.get("emojiId")) == null) {
                return false;
            }
            if (role.has("isSuggestion") && !role.get("isSuggestion").isJsonNull() && (!role.get("isSuggestion").isJsonPrimitive() || !role.get("isSuggestion").getAsJsonPrimitive().isBoolean())) {
                return false;
            }
        }
        return true;
    }

    private boolean isRequiredString(JsonObject object, String key, int maxLength) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return false;
        }
        JsonElement element = object.get(key);
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && !TextUtils.isEmpty(element.getAsString()) && element.getAsString().length() <= maxLength;
    }

    private boolean isExpectedPillsLayout(String value) {
        if (value.length() > 4096 || TextUtils.isEmpty(value)) {
            return TextUtils.isEmpty(value);
        }
        HashSet<Integer> ids = new HashSet<>();
        for (String part : value.split(",")) {
            try {
                int id = Integer.parseInt(part.trim());
                if (id <= 0 || id > 100000 || !ids.add(id)) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return ids.size() <= 100;
    }

    public void exportSettings(BaseFragment fragment) {
        File file = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), generateBackupName(null));
        if (file.exists()) {
            file.delete();
        }
        try {
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            writer.write(getBackup(true));
            writer.flush();
            writer.close();
            fragment.showDialog(new ShareAlert(fragment.getParentActivity(), null, null, file.getAbsolutePath(), null, null, false, null, null, false, false, false, null, null) {
                @Override
                protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                    if (showToast) {
                        AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.SettingsSaved)).show(), 250);
                    }
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public String getBackup(boolean encrypt) {
        AiConfig.ensureConfigMigrated();
        JsonObject backup = new JsonObject();
        for (String config : configs) {
            JsonObject object = toJsonObject(config, getPreferences(config).getAll());
            if (!object.isEmpty()) {
                backup.add(config, object);
            }
        }
        String json = gson.toJson(backup);
        return encrypt ? InvisibleEncryptor.encode(json) : json;
    }

    private JsonObject toJsonObject(String config, Map<String, ?> values) {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isExpectedValue(config, key, value)) {
                object.add(key, gson.toJsonTree(value));
            }
        }
        return object;
    }

    public void importSettings(File file, Activity activity, INavigationLayout parentLayout) {
        if (!isBackup(file)) {
            return;
        }
        AiConfig.ensureConfigMigrated();
        JsonObject backup = getJsonObject(file);
        for (String config : configs) {
            importConfig(backup, config);
        }
        ExteraConfig.reloadConfig();
        PillStackConfig.reloadConfig();
        SharedConfig.reloadConfig();
        OnDeviceAvailability.onModelConfigChanged(null);
        PluginsController.getInstance().restart();
        LocaleController.getInstance().recreateFormatters();
        Theme.reloadAllResources(activity);
        parentLayout.rebuildAllFragmentViews(false, false);
        NotificationCenter notificationCenter = AccountInstance.getInstance(UserConfig.selectedAccount).getNotificationCenter();
        notificationCenter.postNotificationName(NotificationCenter.reloadInterface);
        notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT);
        notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged);
        notificationCenter.postNotificationName(NotificationCenter.dialogFiltersUpdated);
    }

    private void importConfig(JsonObject backup, String config) {
        JsonObject object = getConfigObject(backup, config);
        if (object == null) {
            return;
        }
        SharedPreferences.Editor editor = getPreferences(config).edit();
        for (String key : object.keySet()) {
            JsonElement value = object.get(key);
            BackupItem item = findBackupItem(config, key);
            if (item == null || !isExpectedValue(config, key, value)) {
                continue;
            }
            if (item.clazz.equals(Boolean.class)) {
                editor.putBoolean(key, value.getAsBoolean());
            } else if (item.clazz.equals(Float.class)) {
                editor.putFloat(key, value.getAsFloat());
            } else if (item.clazz.equals(String.class)) {
                editor.putString(key, value.getAsString());
            } else if (item.clazz.equals(Set.class)) {
                HashSet<String> set = new HashSet<>();
                for (JsonElement element : value.getAsJsonArray()) {
                    set.add(element.getAsString());
                }
                editor.putStringSet(key, set);
            } else {
                editor.putInt(key, value.getAsInt());
            }
            if ("exteraconfig".equals(config) && key.equals("iconPack")) {
                editor.remove("iconPacksLayout");
                editor.remove("iconPacksHidden");
            }
        }
        editor.apply();
    }

    private JsonObject getConfigObject(JsonObject backup, String config) {
        if (backup.has(config) && backup.get(config).isJsonObject()) {
            return backup.getAsJsonObject(config);
        }
        if (!"mainconfig".equals(config)) {
            return null;
        }
        for (String key : backup.keySet()) {
            if (key.matches("^mainconfig\\d+$") && backup.get(key).isJsonObject()) {
                return backup.getAsJsonObject(key);
            }
        }
        return null;
    }

    public JsonObject getJsonObject(File file) {
        try {
            String json = readAndDecryptFile(file);
            if (json != null) {
                return gson.fromJson(json, JsonObject.class);
            }
            return null;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private String readAndDecryptFile(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String content = sb.toString();
            if (InvisibleEncryptor.isEncrypted(content)) {
                content = InvisibleEncryptor.decode(content);
            }
            return content;
        }
    }

    private boolean checkKeys(JsonObject backup) {
        for (String config : configs) {
            JsonObject object = getConfigObject(backup, config);
            if (object == null) {
                continue;
            }
            for (String key : object.keySet()) {
                JsonElement value = object.get(key);
                if (isExpectedValue(config, key, value)) {
                    return true;
                }
                FileLog.e("Unexpected value: " + key + " " + value);
                if (BuildVars.DEBUG_VERSION) {
                    Log.d(TAG, "checkKeys: unexpected value " + config + "." + key + " = " + value);
                }
            }
        }
        return false;
    }

    public boolean isBackup(MessageObject messageObject) {
        String path = ChatUtils.getInstance().getPathToMessage(messageObject);
        if (BuildVars.DEBUG_VERSION && messageObject != null) {
            Log.d(TAG, "isBackup(message): name=" + messageObject.getDocumentName() + " path=" + path);
        }
        return messageObject != null && messageObject.getDocumentName() != null && !TextUtils.isEmpty(path) && isBackup(new File(path));
    }

    public boolean isBackup(File file) {
        if (file == null || !file.getName().toLowerCase().endsWith(".extera")) {
            if (BuildVars.DEBUG_VERSION) {
                Log.d(TAG, "isBackup: not a backup file name: " + file);
            }
            return false;
        }
        JsonObject backup = getJsonObject(file);
        boolean valid = backup != null && checkKeys(backup);
        if (BuildVars.DEBUG_VERSION) {
            Log.d(TAG, "isBackup: " + file + " size=" + file.length() + " parsed=" + (backup != null) + (backup != null ? " configs=" + backup.keySet() : "") + " valid=" + valid);
        }
        return valid;
    }

    public int getDiff(File file) {
        return getDiff(getJsonObject(file));
    }

    public int getDiff(JsonObject backup) {
        if (backup == null) {
            return 0;
        }
        int diff = 0;
        JsonObject current = gson.fromJson(getBackup(false), JsonObject.class);
        for (String key : backup.keySet()) {
            String config = key.matches("^mainconfig\\d+$") ? "mainconfig" : key;
            if (!backup.get(key).isJsonObject()) {
                continue;
            }
            JsonObject object = backup.getAsJsonObject(key);
            if (current.has(config)) {
                JsonObject currentObject = current.getAsJsonObject(config);
                for (String prefKey : object.keySet()) {
                    JsonElement value = object.get(prefKey);
                    JsonElement currentValue = currentObject.get(prefKey);
                    if ((!currentObject.has(prefKey) || !value.equals(currentValue)) && isExpectedValue(config, prefKey, value)) {
                        diff++;
                    }
                }
            } else {
                for (String prefKey : object.keySet()) {
                    if (isExpectedValue(config, prefKey, object.get(prefKey))) {
                        diff++;
                    }
                }
            }
        }
        return diff;
    }

    public static class BackupItem implements Serializable {

        public String key;
        public Class<?> clazz;

        public BackupItem(String key, Class<?> clazz) {
            this.key = key;
            this.clazz = clazz;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            return key.equals(((BackupItem) obj).key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }
}
