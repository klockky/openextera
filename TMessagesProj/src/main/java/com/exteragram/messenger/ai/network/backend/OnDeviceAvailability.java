package com.exteragram.messenger.ai.network.backend;

import android.os.Build;
import android.text.TextUtils;

import com.exteragram.messenger.ai.AiConfig;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.GenerationConfig;
import com.google.mlkit.genai.prompt.GenerativeModel;
import com.google.mlkit.genai.prompt.ModelConfig;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class OnDeviceAvailability {

    // com.google.mlkit.genai.common.FeatureStatus
    public static final int STATUS_UNAVAILABLE = 0;
    public static final int STATUS_DOWNLOADABLE = 1;
    public static final int STATUS_DOWNLOADING = 2;
    public static final int STATUS_AVAILABLE = 3;

    // com.google.mlkit.genai.prompt ModelReleaseStage / ModelPreference
    private static final int RELEASE_STAGE_STABLE = 0;
    private static final int RELEASE_STAGE_PREVIEW = 1;
    private static final int PREFERENCE_FAST = 1;
    private static final int PREFERENCE_FULL = 2;

    public static final int MODEL_KEY_FAST = 1;
    public static final int MODEL_KEY_PREVIEW = 2;

    private static volatile int status = -1;
    private static volatile String modelName;
    private static volatile int modelNameGeneration;
    private static volatile boolean thinkingSupported;
    private static volatile boolean systemPromptSupported;
    private static volatile int tokenLimit;
    private static volatile boolean supportedThisSession;
    private static volatile boolean otherModelSupported;
    private static boolean recheckRequested;

    private static final AtomicBoolean refreshing = new AtomicBoolean();
    private static final AtomicBoolean restored = new AtomicBoolean();
    private static final AtomicBoolean refreshedOnce = new AtomicBoolean();
    private static final AtomicBoolean warmedUp = new AtomicBoolean();
    private static final AtomicInteger configGeneration = new AtomicInteger();
    private static final Set<Integer> downloadingModels = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, Integer> downloadPercents = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> sessionStatuses = new ConcurrentHashMap<>();
    private static final List<Runnable> pendingCallbacks = new ArrayList<>();

    public interface DownloadListener {
        void onStarted(long totalBytes);

        void onProgress(float progress, long downloadedBytes);

        void onCompleted();

        void onFailed(Exception e);
    }

    private static boolean isStatusSupported(int status) {
        return status == STATUS_AVAILABLE || status == STATUS_DOWNLOADABLE || status == STATUS_DOWNLOADING;
    }

    public static int getModelKey() {
        return (AiConfig.getOnDevicePreviewModel() ? MODEL_KEY_PREVIEW : 0) | (AiConfig.getOnDeviceFastModel() ? MODEL_KEY_FAST : 0);
    }

    public static GenerativeModel createModel() {
        return createModel(getModelKey());
    }

    private static GenerativeModel createModel(int modelKey) {
        ModelConfig.Builder modelConfig = ModelConfig.builder();
        modelConfig.setReleaseStage((modelKey & MODEL_KEY_PREVIEW) != 0 ? RELEASE_STAGE_PREVIEW : RELEASE_STAGE_STABLE);
        modelConfig.setPreference((modelKey & MODEL_KEY_FAST) != 0 ? PREFERENCE_FAST : PREFERENCE_FULL);
        GenerationConfig.Builder config = new GenerationConfig.Builder();
        config.setModelConfig(modelConfig.build());
        return Generation.INSTANCE.getClient(config.build());
    }

    public static int getConfigGeneration() {
        return configGeneration.get();
    }

    public static void onModelConfigChanged(Runnable onDone) {
        ensureRestored();
        configGeneration.incrementAndGet();
        warmedUp.set(false);
        thinkingSupported = false;
        systemPromptSupported = false;
        tokenLimit = 0;
        AiConfig.getEditor()
                .remove("onDeviceThinkingSupported")
                .remove("onDeviceSystemPromptSupported")
                .remove("onDeviceTokenLimit")
                .apply();
        refresh(onDone);
    }

    public static int getStatus() {
        if (Build.VERSION.SDK_INT < 26) {
            return STATUS_UNAVAILABLE;
        }
        ensureRestored();
        if (refreshedOnce.compareAndSet(false, true)) {
            refresh(null);
        }
        return status;
    }

    public static boolean isReady() {
        return getStatus() == STATUS_AVAILABLE;
    }

    public static String getModelName() {
        ensureRestored();
        return modelName;
    }

    public static boolean isThinkingSupported() {
        ensureRestored();
        return thinkingSupported;
    }

    public static boolean isSystemPromptSupported() {
        ensureRestored();
        return systemPromptSupported;
    }

    public static int getTokenLimit() {
        ensureRestored();
        return tokenLimit;
    }

    public static void ensureMetadata(GenerativeModelFutures model, int generation) {
        ensureRestored();
        if (tokenLimit > 0) {
            return;
        }
        readMetadata(model, generation);
    }

    public static boolean isSupported() {
        return isStatusSupported(getStatus()) || supportedThisSession || otherModelSupported;
    }

    public static boolean needsDownload() {
        return getStatus() == STATUS_DOWNLOADABLE;
    }

    public static boolean isDownloading() {
        return downloadingModels.contains(getModelKey());
    }

    public static boolean isDownloadInProgress() {
        return isDownloading() || getStatus() == STATUS_DOWNLOADING;
    }

    public static int getDownloadPercent() {
        Integer percent = downloadPercents.get(getModelKey());
        return percent != null ? percent : -1;
    }

    public static void report(int status, int generation) {
        ensureRestored();
        refreshedOnce.set(true);
        if (generation == configGeneration.get()) {
            apply(status);
        }
    }

    public static void refresh(Runnable onDone) {
        if (Build.VERSION.SDK_INT < 26) {
            apply(STATUS_UNAVAILABLE);
            runOnDone(onDone);
            return;
        }
        synchronized (pendingCallbacks) {
            if (onDone != null) {
                pendingCallbacks.add(onDone);
            }
            if (!refreshing.compareAndSet(false, true)) {
                recheckRequested = true;
                return;
            }
            ensureRestored();
            refreshedOnce.set(true);
            runOnWorker(OnDeviceAvailability::check);
        }
    }

    private static void check() {
        ArrayList<Runnable> callbacks;
        while (true) {
            int generation = configGeneration.get();
            int modelKey = getModelKey();
            int newStatus = checkStatus(generation);
            if (generation != configGeneration.get()) {
                continue;
            }
            sessionStatuses.put(modelKey, newStatus);
            if (newStatus == STATUS_UNAVAILABLE) {
                updateOtherModelSupported(modelKey);
            }
            if (modelNameGeneration != generation && modelName != null) {
                modelName = null;
                AiConfig.getEditor().remove("onDeviceModelName").apply();
                notifyServicesUpdated();
            }
            apply(newStatus);
            synchronized (pendingCallbacks) {
                if (recheckRequested || generation != configGeneration.get()) {
                    recheckRequested = false;
                    continue;
                }
                refreshing.set(false);
                callbacks = new ArrayList<>(pendingCallbacks);
                pendingCallbacks.clear();
            }
            break;
        }
        for (Runnable callback : callbacks) {
            runOnDone(callback);
        }
    }

    private static void updateOtherModelSupported(int currentKey) {
        boolean supported = false;
        for (int key = 0; key <= 3 && !supported; key++) {
            if (key == currentKey) {
                continue;
            }
            Integer keyStatus = sessionStatuses.get(key);
            if (keyStatus == null) {
                keyStatus = probeStatus(key);
                sessionStatuses.put(key, keyStatus);
            }
            supported = isStatusSupported(keyStatus);
        }
        if (supported != otherModelSupported) {
            otherModelSupported = supported;
            AiConfig.getEditor().putBoolean("onDeviceOtherModelSupported", supported).apply();
            notifyServicesUpdated();
        }
    }

    private static int probeStatus(int modelKey) {
        GenerativeModel model = null;
        try {
            model = createModel(modelKey);
            Integer result = GenerativeModelFutures.from(model).checkStatus().get(10, TimeUnit.SECONDS);
            return result == null ? -1 : result;
        } catch (Throwable e) {
            FileLog.e("AI_ON_DEVICE_PROBE_FAILED", e);
            return STATUS_UNAVAILABLE;
        } finally {
            closeQuietly(model);
        }
    }

    private static int checkStatus(int generation) {
        GenerativeModel model = null;
        try {
            model = createModel();
            GenerativeModelFutures futures = GenerativeModelFutures.from(model);
            Integer result = futures.checkStatus().get(10, TimeUnit.SECONDS);
            int newStatus = result == null ? -1 : result;
            if (newStatus == STATUS_AVAILABLE) {
                readMetadata(futures, generation);
            } else if (isStatusSupported(newStatus) && readModelName(futures, generation)) {
                notifyServicesUpdated();
            }
            return newStatus;
        } catch (Throwable e) {
            FileLog.e("AI_ON_DEVICE_STATUS_FAILED", e);
            return STATUS_UNAVAILABLE;
        } finally {
            closeQuietly(model);
        }
    }

    private static boolean readModelName(GenerativeModelFutures model, int generation) {
        try {
            String name = model.getBaseModelName().get(10, TimeUnit.SECONDS);
            if (generation != configGeneration.get() || TextUtils.isEmpty(name)) {
                return false;
            }
            modelNameGeneration = generation;
            if (name.equals(modelName)) {
                return false;
            }
            modelName = name;
            AiConfig.getEditor().putString("onDeviceModelName", name).apply();
            return true;
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_MODEL_NAME", e);
            return false;
        }
    }

    private static void readMetadata(GenerativeModelFutures model, int generation) {
        boolean changed = readModelName(model, generation);
        try {
            boolean thinking = Boolean.TRUE.equals(model.isThinkingModeAvailable().get(10, TimeUnit.SECONDS));
            if (generation != configGeneration.get()) {
                return;
            }
            if (thinking != thinkingSupported) {
                thinkingSupported = thinking;
                AiConfig.getEditor().putBoolean("onDeviceThinkingSupported", thinking).apply();
                changed = true;
            }
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_THINKING", e);
        }
        try {
            boolean systemPrompt = Boolean.TRUE.equals(model.isSystemPromptAvailable().get(10, TimeUnit.SECONDS));
            if (generation != configGeneration.get()) {
                return;
            }
            if (systemPrompt != systemPromptSupported) {
                systemPromptSupported = systemPrompt;
                AiConfig.getEditor().putBoolean("onDeviceSystemPromptSupported", systemPrompt).apply();
            }
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_SYSTEM_PROMPT", e);
        }
        try {
            Integer limit = model.getTokenLimit().get(10, TimeUnit.SECONDS);
            if (generation != configGeneration.get()) {
                return;
            }
            if (limit != null && limit > 0 && limit != tokenLimit) {
                tokenLimit = limit;
                AiConfig.getEditor().putInt("onDeviceTokenLimit", limit).apply();
            }
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_TOKEN_LIMIT", e);
        }
        if (changed) {
            notifyServicesUpdated();
        }
    }

    public static void warmup() {
        if (AiConfig.ON_DEVICE_SERVICE.getId().equals(AiConfig.getSelectedServiceId()) && isReady() && warmedUp.compareAndSet(false, true)) {
            runOnWorker(() -> {
                GenerativeModel model = null;
                try {
                    model = createModel();
                    GenerativeModelFutures.from(model).warmup().get(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    warmedUp.set(false);
                } catch (Throwable e) {
                    warmedUp.set(false);
                    FileLog.e("AI_ON_DEVICE_WARMUP", e);
                } finally {
                    closeQuietly(model);
                }
            });
        }
    }

    public static void download(int modelKey, DownloadListener listener) {
        if (Build.VERSION.SDK_INT < 26) {
            listener.onFailed(new IllegalStateException("unsupported android version"));
            return;
        }
        if (downloadingModels.add(modelKey)) {
            downloadPercents.remove(modelKey);
            notifyServicesUpdated();
            runOnWorker(() -> runDownload(modelKey, listener));
        }
    }

    private static void runDownload(int modelKey, DownloadListener listener) {
        GenerativeModel model = null;
        try {
            model = createModel(modelKey);
            GenerativeModelFutures futures = GenerativeModelFutures.from(model);
            long[] totalBytes = {0};
            futures.download(new DownloadCallback() {
                @Override
                public void onDownloadStarted(long bytesToDownload) {
                    totalBytes[0] = bytesToDownload;
                    listener.onStarted(bytesToDownload);
                }

                @Override
                public void onDownloadProgress(long totalBytesDownloaded) {
                    long total = totalBytes[0];
                    float progress = total > 0 ? Math.min(1.0f, totalBytesDownloaded / (float) total) : -1.0f;
                    int percent = progress < 0 ? -1 : (int) (100.0f * progress);
                    Integer previous = downloadPercents.put(modelKey, percent);
                    if (previous == null || previous != percent) {
                        notifyServicesUpdated();
                        listener.onProgress(progress, totalBytesDownloaded);
                    }
                }

                @Override
                public void onDownloadFailed(GenAiException e) {
                    FileLog.e("AI_ON_DEVICE_DOWNLOAD_FAILED", e);
                }
            }).get();
            int newStatus = futures.checkStatus().get(10, TimeUnit.SECONDS);
            if (modelKey == getModelKey()) {
                refresh(null);
            }
            if (newStatus == STATUS_AVAILABLE) {
                listener.onProgress(1.0f, totalBytes[0]);
                listener.onCompleted();
            } else if (newStatus != STATUS_DOWNLOADING) {
                listener.onFailed(new IllegalStateException("model is not available after download, status=" + newStatus));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FileLog.e("AI_ON_DEVICE_DOWNLOAD", e);
            listener.onFailed(e);
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_DOWNLOAD", e);
            listener.onFailed(e);
        } finally {
            closeQuietly(model);
            downloadPercents.remove(modelKey);
            downloadingModels.remove(modelKey);
            notifyServicesUpdated();
        }
    }

    private static void ensureRestored() {
        if (restored.compareAndSet(false, true)) {
            status = AiConfig.getPreferences().getInt("onDeviceFeatureStatus", -1);
            modelName = AiConfig.getPreferences().getString("onDeviceModelName", null);
            thinkingSupported = AiConfig.getPreferences().getBoolean("onDeviceThinkingSupported", false);
            systemPromptSupported = AiConfig.getPreferences().getBoolean("onDeviceSystemPromptSupported", false);
            tokenLimit = AiConfig.getPreferences().getInt("onDeviceTokenLimit", 0);
            otherModelSupported = AiConfig.getPreferences().getBoolean("onDeviceOtherModelSupported", false);
        }
    }

    private static void apply(int newStatus) {
        if (isStatusSupported(newStatus)) {
            supportedThisSession = true;
        }
        if (status == newStatus) {
            return;
        }
        status = newStatus;
        AiConfig.getEditor().putInt("onDeviceFeatureStatus", newStatus).apply();
        notifyServicesUpdated();
    }

    private static void notifyServicesUpdated() {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.servicesUpdated));
    }

    private static void runOnWorker(Runnable runnable) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                runnable.run();
            } finally {
                executor.shutdown();
            }
        });
    }

    static void closeQuietly(GenerativeModel model) {
        if (model == null) {
            return;
        }
        try {
            model.close();
        } catch (Throwable ignore) {
        }
    }

    private static void runOnDone(Runnable runnable) {
        if (runnable != null) {
            AndroidUtilities.runOnUIThread(runnable);
        }
    }
}
