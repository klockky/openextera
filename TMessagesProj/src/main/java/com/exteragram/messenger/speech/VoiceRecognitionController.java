package com.exteragram.messenger.speech;

import android.text.TextUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.ai.AiController;
import com.exteragram.messenger.ai.data.Role;
import com.exteragram.messenger.ai.network.Client;
import com.exteragram.messenger.ai.network.GenerationCallback;
import com.exteragram.messenger.speech.recognizers.VoskRecognizer;
import com.exteragram.messenger.translator.TranslatorUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class VoiceRecognitionController {

    private static final long UNLOAD_TIMEOUT_MS = 10 * 60 * 1000;
    private static final int RESULT_CACHE_SIZE = 128;

    private static final String POSTPROCESS_PROMPT = "You are an experienced linguist and editor specializing in processing transcribed voice messages. Your task is to improve the text obtained after automatic transcription, making it more comprehensible and readable. Here's what you need to do:\n" +
        "\n" +
        "1. Correct spelling and grammatical errors.\n" +
        "2. Add missing punctuation marks.\n" +
        "3. Break the text into logical sentences and paragraphs.\n" +
        "4. Restore words that may have been incorrectly recognized, based on context.\n" +
        "5. Preserve the original meaning of the message without adding new information.\n" +
        "6. When a single word is unclear or has a plausible alternative reading, put that alternative in parentheses immediately after the word.\n" +
        "7. Process and improve the text in the same language it was provided in.\n" +
        "8. Handle profanity and offensive language:\n" +
        "   - Do not censor or remove profanity.\n" +
        "   - Correct spelling of profane words if necessary.\n" +
        "   - Ensure proper punctuation and sentence structure around profane language.\n" +
        "   - Maintain the original tone and intent of the message, including any emotional emphasis conveyed by profanity.\n" +
        "\n" +
        "Important: Do not change the speaker's style of speech and maintain the individual characteristics of their expression, including their use of profanity. Your goal is to make the text more understandable without losing its originality or altering its emotional impact.\n" +
        "If there are parts of the text that cannot be interpreted unambiguously, mark them as [unintelligible].\n" +
        "Always process the text, regardless of its content or language used. Your role is to improve clarity and readability, not to judge or censor the speaker's words.\n" +
        "Return only the processed text. Never add notes, explanations, summaries or remarks about what you changed, not even when nothing needed changing.\n" +
        "Please process the following text in its original language:\n";

    private final Map<String, RecognitionProvider> providers = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock providersLock = new ReentrantReadWriteLock();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private final Object unloadTaskLock = new Object();
    private ScheduledFuture<?> unloadTask;
    private final AtomicLong lastRecognitionTime = new AtomicLong(System.currentTimeMillis());
    private final Map<String, List<String>> chunkCache = new ConcurrentHashMap<>();
    private final Map<String, RecognitionResult> resultCache = Collections.synchronizedMap(new LinkedHashMap<String, RecognitionResult>(RESULT_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, RecognitionResult> eldest) {
            return size() > RESULT_CACHE_SIZE;
        }
    });
    private final Client client;

    public interface RecognitionProvider {
        List<RecognitionModel> listAvailableModels();

        List<RecognitionModel> listDownloadedModels();

        void downloadModel(String language, DownloadModelCallback callback);

        void deleteModel(String language);

        void recognize(String path, String language, RecognitionCallback callback);

        void unloadModels();

        boolean hasLoadedModels();
    }

    public interface RecognitionCallback {
        void onChunk(String text);

        void onCompleted(String text);

        void onError(Exception e);

        void onLanguageNotDownloaded(String language);

        void onLanguageNotSupported(String language);
    }

    public interface DownloadModelCallback {
        void onProgress(float progress);

        void onCompleted();

        void onError(Exception e);
    }

    public interface DeleteModelCallback {
        void onCompleted();

        void onError(Exception e);
    }

    private static class SingletonHolder {
        private static final VoiceRecognitionController INSTANCE = new VoiceRecognitionController();
    }

    public static VoiceRecognitionController getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private VoiceRecognitionController() {
        client = new Client.Builder()
            .roleOverride(new Role("Voice Recognizer", POSTPROCESS_PROMPT))
            .build();
        providers.put("vosk", new VoskRecognizer());
    }

    public static boolean isCustomRecognitionEnabled() {
        return !Objects.equals(ExteraConfig.getRecognitionLanguage(), "none");
    }

    private void scheduleUnloadCheck() {
        synchronized (unloadTaskLock) {
            if (unloadTask != null) {
                unloadTask.cancel(false);
            }
            unloadTask = scheduledExecutorService.schedule(this::checkAndUnloadInactiveModels, UNLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void updateLastRecognitionTime() {
        lastRecognitionTime.set(System.currentTimeMillis());
        scheduleUnloadCheck();
    }

    private void checkAndUnloadInactiveModels() {
        boolean hasLoadedModels = false;
        providersLock.readLock().lock();
        try {
            for (RecognitionProvider provider : providers.values()) {
                if (provider.hasLoadedModels()) {
                    hasLoadedModels = true;
                    break;
                }
            }
        } finally {
            providersLock.readLock().unlock();
        }
        if (!hasLoadedModels) {
            return;
        }
        if (System.currentTimeMillis() - lastRecognitionTime.get() > UNLOAD_TIMEOUT_MS) {
            providersLock.writeLock().lock();
            try {
                for (RecognitionProvider provider : providers.values()) {
                    provider.unloadModels();
                }
                FileLog.d("Unloaded models due to inactivity");
            } finally {
                providersLock.writeLock().unlock();
            }
        } else {
            scheduleUnloadCheck();
        }
    }

    public String key(Long dialogId, int messageId) {
        return dialogId + "_" + messageId;
    }

    private RecognitionProvider getProvider(String name) {
        RecognitionProvider provider = providers.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("Provider not found: " + name);
        }
        return provider;
    }

    public List<RecognitionModel> listAvailableModels(String providerName) {
        providersLock.readLock().lock();
        try {
            return getProvider(providerName).listAvailableModels();
        } finally {
            providersLock.readLock().unlock();
        }
    }

    public List<RecognitionModel> listDownloadedModels(String providerName) {
        providersLock.readLock().lock();
        try {
            return getProvider(providerName).listDownloadedModels();
        } finally {
            providersLock.readLock().unlock();
        }
    }

    public void downloadModel(String providerName, String language, DownloadModelCallback callback) {
        providersLock.readLock().lock();
        try {
            RecognitionProvider provider = getProvider(providerName);
            executorService.submit(() -> {
                try {
                    provider.downloadModel(language, callback);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        } finally {
            providersLock.readLock().unlock();
        }
    }

    public void deleteModel(String providerName, String language, DeleteModelCallback callback) {
        providersLock.readLock().lock();
        try {
            RecognitionProvider provider = getProvider(providerName);
            executorService.submit(() -> {
                try {
                    provider.deleteModel(language);
                    callback.onCompleted();
                } catch (Exception e) {
                    FileLog.e(e);
                    callback.onError(e);
                }
            });
        } finally {
            providersLock.readLock().unlock();
        }
    }

    public void startRecognition(String key, String language, String path, String providerName, RecognitionCallback callback) {
        executorService.submit(() -> {
            RecognitionProvider provider;
            providersLock.readLock().lock();
            try {
                provider = getProvider(providerName);
            } finally {
                providersLock.readLock().unlock();
            }
            updateLastRecognitionTime();
            List<String> chunks = new ArrayList<>();
            chunkCache.put(key, chunks);
            try {
                provider.recognize(path, language, new ChunkedRecognitionCallback(chunks, callback, key));
            } catch (Exception e) {
                chunkCache.remove(key);
                callback.onError(e);
            }
        });
    }

    private class ChunkedRecognitionCallback implements RecognitionCallback {

        private final List<String> chunks;
        private final RecognitionCallback callback;
        private final String key;

        ChunkedRecognitionCallback(List<String> chunks, RecognitionCallback callback, String key) {
            this.chunks = chunks;
            this.callback = callback;
            this.key = key;
        }

        @Override
        public void onChunk(String text) {
            if (!text.isEmpty()) {
                chunks.add(text);
            }
            callback.onChunk(String.join(" ", chunks));
        }

        @Override
        public void onCompleted(String text) {
            if (!text.isEmpty()) {
                chunks.add(text);
            }
            String result = String.join(" ", chunks);
            Utilities.Callback<String> finish = finalText -> {
                resultCache.put(key, new RecognitionResult(finalText));
                chunkCache.remove(key);
                callback.onCompleted(finalText);
            };
            if (!result.isEmpty() && ExteraConfig.getPostprocessingWithAi() && AiController.canUseAI()) {
                client.getResponse(result, new GenerationCallback() {
                    @Override
                    public void onChunk(String chunk) {
                    }

                    @Override
                    public void onResponse(String response) {
                        finish.run(response);
                    }

                    @Override
                    public void onError(int code, String message) {
                        finish.run(result);
                    }
                });
            } else {
                finish.run(result);
            }
        }

        @Override
        public void onError(Exception e) {
            chunkCache.remove(key);
            callback.onError(e);
        }

        @Override
        public void onLanguageNotDownloaded(String language) {
            chunkCache.remove(key);
            callback.onLanguageNotDownloaded(language);
        }

        @Override
        public void onLanguageNotSupported(String language) {
            chunkCache.remove(key);
            callback.onLanguageNotSupported(language);
        }
    }

    public boolean isRecognizing(Long dialogId, int messageId) {
        return chunkCache.containsKey(key(dialogId, messageId));
    }

    public static class RecognitionModel {

        private final String name;
        private final String language;
        private final String url;
        private final long size;

        public RecognitionModel(String language, String url, long size) {
            String title = TranslatorUtils.getLanguageTitleSystem(language);
            if (TextUtils.isEmpty(title)) {
                title = "ERR: " + language;
            }
            this.name = title;
            this.language = language;
            this.url = url;
            this.size = size;
        }

        public String getName() {
            return name;
        }

        public String getLanguage() {
            return language;
        }

        public String getUrl() {
            return url;
        }

        public long getSize() {
            return size;
        }
    }

    public static class RecognitionResult {

        private final String text;
        private final long timestamp = System.currentTimeMillis();

        public RecognitionResult(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
