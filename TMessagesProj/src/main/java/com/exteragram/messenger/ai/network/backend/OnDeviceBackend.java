package com.exteragram.messenger.ai.network.backend;

import android.os.Build;
import android.text.TextUtils;

import com.exteragram.messenger.ai.data.Message;
import com.exteragram.messenger.ai.data.Role;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.common.StreamingCallback;
import com.google.mlkit.genai.prompt.CachedContext;
import com.google.mlkit.genai.prompt.Candidate;
import com.google.mlkit.genai.prompt.CreateCachedContextRequest;
import com.google.mlkit.genai.prompt.GenerateContentRequest;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.GenerativeModel;
import com.google.mlkit.genai.prompt.ImagePart;
import com.google.mlkit.genai.prompt.PromptPrefix;
import com.google.mlkit.genai.prompt.SystemInstruction;
import com.google.mlkit.genai.prompt.TextPart;
import com.google.mlkit.genai.prompt.java.CachesFutures;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class OnDeviceBackend implements Backend {

    private static final String CACHE_PREFIX = "extera_role_";
    private static final int MAX_CACHED_CONTEXTS = 4;
    // GenAiException error code reported as "-4" (prompt too large) to the UI
    private static final int ERROR_REQUEST_TOO_LARGE = 12;

    private static volatile boolean cachingUnsupported;

    private static boolean isInferenceFailure(int errorCode) {
        switch (errorCode) {
            case -101:
            case 7:
            case 8:
            case 9:
            case 12:
            case 16:
            case 27:
            case 30:
            case 501:
            case 604:
                return false;
            default:
                return true;
        }
    }

    @Override
    public int getHistoryCharsLimit() {
        return 12000;
    }

    @Override
    public int getHistoryMessagesLimit() {
        return 24;
    }

    @Override
    public int getMaxOutputTokens() {
        return 1024;
    }

    @Override
    public float getMaxTemperature() {
        return 1.0f;
    }

    @Override
    public boolean supportsImages() {
        return true;
    }

    @Override
    public void execute(BackendRequest request, BackendSink sink) {
        if (Build.VERSION.SDK_INT < 26) {
            sink.onError(-1, "on-device model requires android 8.0");
            return;
        }
        int generation = OnDeviceAvailability.getConfigGeneration();
        GenerativeModel model = null;
        try {
            model = OnDeviceAvailability.createModel();
            GenerativeModelFutures futures = GenerativeModelFutures.from(model);
            int status = futures.checkStatus().get(10, TimeUnit.SECONDS);
            OnDeviceAvailability.report(status, generation);
            if (!sink.isActive()) {
                return;
            }
            if (status == OnDeviceAvailability.STATUS_UNAVAILABLE) {
                sink.onError(-1, "on-device model is not available");
            } else if (status == OnDeviceAvailability.STATUS_DOWNLOADABLE) {
                sink.onError(-2, "on-device model is not downloaded");
            } else if (status == OnDeviceAvailability.STATUS_DOWNLOADING) {
                sink.onError(-3, "on-device model is downloading");
            } else {
                OnDeviceAvailability.ensureMetadata(futures, generation);
                generate(request, sink, futures, model);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (sink.isActive()) {
                FileLog.e("AI_ON_DEVICE_FAILED", e);
                GenAiException genAiException = findGenAiException(e);
                String message = e.getMessage() != null ? e.getMessage() : "Unknown error";
                if (genAiException != null && genAiException.getErrorCode() == ERROR_REQUEST_TOO_LARGE) {
                    sink.onError(-4, message);
                } else {
                    sink.onError(500, message);
                }
            }
        } finally {
            OnDeviceAvailability.closeQuietly(model);
        }
    }

    private static GenAiException findGenAiException(Throwable throwable) {
        while (throwable != null) {
            if (throwable instanceof GenAiException) {
                return (GenAiException) throwable;
            }
            throwable = throwable.getCause();
        }
        return null;
    }

    private void generate(BackendRequest request, BackendSink sink, GenerativeModelFutures futures, GenerativeModel model) throws ExecutionException, InterruptedException {
        Role role = request.role();
        String instruction = buildInstruction(role != null ? role.getPrompt() : null);
        boolean thinking = request.service().isReasoningEnabled() && OnDeviceAvailability.isThinkingSupported();
        SystemInstruction systemInstruction;
        String promptPrefix;
        if (OnDeviceAvailability.isSystemPromptSupported()) {
            systemInstruction = new SystemInstruction(instruction);
            promptPrefix = "";
        } else {
            systemInstruction = null;
            promptPrefix = instruction + "\n\n";
        }
        ImagePart imagePart = createImagePart(request.currentMessage());
        List<Message> history = request.history();
        int tokenLimit = resolveTokenLimit() - request.maxOutputTokens() - 256;

        int historyOffset = 0;
        while (sink.isActive()) {
            String prompt = buildPrompt(history, request.currentMessage(), historyOffset);
            int tokens = countTokens(futures, createRequest(request, systemInstruction, null, imagePart, promptPrefix + prompt, thinking));
            if (tokens > tokenLimit && historyOffset < history.size()) {
                historyOffset += 2;
                continue;
            }
            if (tokens > tokenLimit) {
                sink.onError(-4, "prompt exceeds the on-device token limit");
                return;
            }
            if (!sink.isActive()) {
                return;
            }

            String cachedContextName = null;
            if (systemInstruction == null && imagePart == null) {
                cachedContextName = ensureCachedContext(model, instruction);
            }
            GenerateContentRequest contentRequest;
            if (cachedContextName != null) {
                contentRequest = createRequest(request, null, cachedContextName, imagePart, prompt, thinking);
            } else {
                contentRequest = createRequest(request, systemInstruction, null, imagePart, promptPrefix + prompt, thinking);
            }

            StringBuffer streamed = new StringBuffer();
            ListenableFuture<GenerateContentResponse> future;
            if (request.stream()) {
                future = futures.generateContent(contentRequest, new StreamingCallback() {
                    @Override
                    public void onNewText(String text) {
                        if (!sink.isActive() || TextUtils.isEmpty(text)) {
                            return;
                        }
                        streamed.append(text);
                        sink.onChunk(streamed.toString());
                    }

                    @Override
                    public void onNewThought(String thought) {
                        if (sink.isActive()) {
                            sink.onThinking();
                        }
                    }
                });
            } else {
                future = futures.generateContent(contentRequest);
            }
            sink.onCancellable(() -> future.cancel(true));
            try {
                GenerateContentResponse response = future.get();
                if (!sink.isActive()) {
                    return;
                }
                String text = extractText(response);
                if (TextUtils.isEmpty(text)) {
                    text = streamed.toString();
                }
                text = trimTrailing(text);
                if (TextUtils.isEmpty(text)) {
                    sink.onError(204, "Response body is empty");
                } else {
                    sink.onComplete(text);
                }
            } catch (ExecutionException e) {
                GenAiException genAiException = findGenAiException(e);
                if (genAiException == null || !isInferenceFailure(genAiException.getErrorCode())) {
                    throw e;
                }
                if (sink.isActive()) {
                    FileLog.e("AI_ON_DEVICE_INFERENCE_FAILED", e);
                    sink.onError(-5, genAiException.getMessage() != null ? genAiException.getMessage() : "Inference failed");
                }
            }
            return;
        }
    }

    private GenerateContentRequest createRequest(BackendRequest request, SystemInstruction systemInstruction, String cachedContextName, ImagePart imagePart, String prompt, boolean thinking) {
        TextPart textPart = new TextPart(prompt);
        GenerateContentRequest.Builder builder;
        if (systemInstruction != null && imagePart != null) {
            builder = new GenerateContentRequest.Builder(systemInstruction, imagePart, textPart);
        } else if (systemInstruction != null) {
            builder = new GenerateContentRequest.Builder(systemInstruction, textPart);
        } else if (imagePart != null) {
            builder = new GenerateContentRequest.Builder(imagePart, textPart);
        } else {
            builder = new GenerateContentRequest.Builder(textPart);
        }
        builder.setTemperature(request.temperature());
        builder.setMaxOutputTokens(request.maxOutputTokens());
        builder.setEnableThinking(thinking);
        if (cachedContextName != null) {
            builder.setCachedContextName(cachedContextName);
        }
        return builder.build();
    }

    private String ensureCachedContext(GenerativeModel model, String instruction) {
        if (cachingUnsupported || TextUtils.isEmpty(instruction)) {
            return null;
        }
        String hash = Utilities.MD5(instruction);
        if (TextUtils.isEmpty(hash)) {
            return null;
        }
        String name = CACHE_PREFIX + hash;
        try {
            CachesFutures caches = CachesFutures.from(model);
            ArrayList<String> staleContexts = new ArrayList<>();
            List<CachedContext> contexts = caches.list().get(10, TimeUnit.SECONDS);
            if (contexts != null) {
                for (CachedContext context : contexts) {
                    String contextName = context == null ? null : context.getName();
                    if (contextName == null || !contextName.startsWith(CACHE_PREFIX)) {
                        continue;
                    }
                    if (name.equals(contextName)) {
                        return name;
                    }
                    staleContexts.add(contextName);
                }
            }
            if (staleContexts.size() >= MAX_CACHED_CONTEXTS) {
                for (String staleName : staleContexts) {
                    caches.delete(staleName).get(10, TimeUnit.SECONDS);
                }
            }
            CachedContext cachedContext = caches.create(new CreateCachedContextRequest.Builder(name, new PromptPrefix(instruction)).build()).get(30, TimeUnit.SECONDS);
            return cachedContext == null ? null : cachedContext.getName();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            cachingUnsupported = true;
            FileLog.e("AI_ON_DEVICE_CACHE", e);
        }
        return null;
    }

    private String buildInstruction(String rolePrompt) {
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(rolePrompt)) {
            sb.append(rolePrompt).append("\n\n");
        }
        sb.append("Unless told otherwise, always reply in the same language the user writes in.");
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        String language = locale != null ? locale.getDisplayLanguage(Locale.ENGLISH) : null;
        if (!TextUtils.isEmpty(language)) {
            sb.append(" If the language is unclear, reply in ").append(language).append('.');
        }
        return sb.toString();
    }

    private String buildPrompt(List<Message> history, Message current, int offset) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < history.size(); i++) {
            Message message = history.get(i);
            if (message != null && !TextUtils.isEmpty(message.content())) {
                sb.append("assistant".equals(message.role()) ? "Assistant: " : "User: ");
                sb.append(message.content());
                sb.append("\n\n");
            }
        }
        sb.append("User: ").append(current.content());
        return sb.toString();
    }

    private ImagePart createImagePart(Message message) {
        byte[] imageData = message.getImageData();
        if (imageData != null && imageData.length != 0) {
            try {
                return new ImagePart(imageData);
            } catch (Exception e) {
                FileLog.e("AI_ON_DEVICE_IMAGE_REJECTED", e);
            }
        }
        return null;
    }

    private int resolveTokenLimit() {
        int limit = OnDeviceAvailability.getTokenLimit();
        return limit > 0 ? limit : 4000;
    }

    private int countTokens(GenerativeModelFutures futures, GenerateContentRequest request) {
        try {
            return futures.countTokens(request).get(10, TimeUnit.SECONDS).getTotalTokens();
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_COUNT_TOKENS", e);
            return 0;
        }
    }

    private String extractText(GenerateContentResponse response) {
        if (response == null) {
            return null;
        }
        List<Candidate> candidates = response.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.get(0).getText();
    }

    private String trimTrailing(String text) {
        if (text == null) {
            return null;
        }
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }
}
