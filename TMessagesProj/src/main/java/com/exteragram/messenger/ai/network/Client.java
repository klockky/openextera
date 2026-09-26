package com.exteragram.messenger.ai.network;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.exteragram.messenger.ai.AiConfig;
import com.exteragram.messenger.ai.AiController;
import com.exteragram.messenger.ai.data.Message;
import com.exteragram.messenger.ai.data.Role;
import com.exteragram.messenger.ai.data.Service;
import com.exteragram.messenger.ai.network.backend.Backend;
import com.exteragram.messenger.ai.network.backend.BackendRequest;
import com.exteragram.messenger.ai.network.backend.BackendSink;
import com.exteragram.messenger.ai.network.backend.OnDeviceBackend;
import com.exteragram.messenger.ai.network.backend.RestBackend;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {

    private static final int MAX_IMAGE_SIZE = 4 * 1024 * 1024;
    private static final int MAX_IMAGE_SIDE = 2048;
    private static final int MAX_HISTORY_MESSAGES = 64;
    private static final int MAX_HISTORY_CHARS = 64000;

    private final AtomicBoolean isGenerating = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, ExecutorService> activeRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Runnable> activeCancellables = new ConcurrentHashMap<>();
    private final RestBackend restBackend = new RestBackend();
    private final OnDeviceBackend onDeviceBackend = new OnDeviceBackend();
    private final Service serviceOverride;
    private final Role roleOverride;

    private Client(Builder builder) {
        serviceOverride = builder.serviceOverride;
        roleOverride = builder.roleOverride;
    }

    public static String getMimeType(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".heic") || lower.endsWith(".heif")) {
            return "image/heic";
        }
        return "image/jpeg";
    }

    private static ImagePayload loadImagePayload(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile() || file.length() == 0) {
            return null;
        }
        if (file.length() > MAX_IMAGE_SIZE) {
            return compressImage(path);
        }
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(file.length(), MAX_IMAGE_SIZE))) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new ImagePayload(out.toByteArray(), getMimeType(path));
        } catch (IOException e) {
            FileLog.e("Error loading image: " + path, e);
            return null;
        }
    }

    private static ImagePayload compressImage(String path) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (bounds.outWidth / options.inSampleSize > MAX_IMAGE_SIDE || bounds.outHeight / options.inSampleSize > MAX_IMAGE_SIDE) {
                options.inSampleSize *= 2;
            }
            Bitmap bitmap = BitmapFactory.decodeFile(path, options);
            if (bitmap == null) {
                return null;
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                int quality = 85;
                do {
                    out.reset();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
                    quality -= 10;
                    if (out.size() <= MAX_IMAGE_SIZE) {
                        break;
                    }
                } while (quality >= 55);
                if (out.size() <= MAX_IMAGE_SIZE) {
                    return new ImagePayload(out.toByteArray(), "image/jpeg");
                }
                return null;
            } finally {
                bitmap.recycle();
            }
        } catch (Exception e) {
            FileLog.e("Error compressing image: " + path, e);
            return null;
        }
    }

    private Service getSelectedService() {
        return serviceOverride != null ? serviceOverride : AiController.getInstance().getSelected();
    }

    private Backend resolveBackend(Service service) {
        return service != null && service.isOnDevice() ? onDeviceBackend : restBackend;
    }

    private Role getActiveRole() {
        if (roleOverride != null) {
            return roleOverride;
        }
        if (serviceOverride == null) {
            return AiController.getInstance().getSelectedRole();
        }
        return null;
    }

    public String getResponse(String prompt, GenerationCallback callback) {
        return getResponse(prompt, false, false, null, callback);
    }

    public String getResponse(String prompt, boolean useHistory, boolean stream, String imagePath, GenerationCallback callback) {
        String requestId = UUID.randomUUID().toString();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        activeRequests.put(requestId, executor);
        executor.execute(() -> {
            isGenerating.set(true);
            try {
                Service service = getSelectedService();
                Backend backend = resolveBackend(service);
                ImagePayload image = backend.supportsImages() && AiController.canSendImage(imagePath) ? loadImagePayload(imagePath) : null;
                if (!activeRequests.containsKey(requestId)) {
                    return;
                }
                Message message = new Message("user", prompt, image != null ? image.data() : null, image != null ? image.mimeType() : null);
                ArrayList<Message> history = useHistory ? historyForRequest(AiConfig.getConversationHistory(), backend) : new ArrayList<>();
                if (!activeRequests.containsKey(requestId)) {
                    return;
                }
                float temperature = AiConfig.getTemperature() / 20.0f * backend.getMaxTemperature();
                BackendRequest request = new BackendRequest(service, getActiveRole(), history, message, stream, temperature, backend.getMaxOutputTokens());
                backend.execute(request, new RequestSink(requestId, message, useHistory, callback));
            } catch (Exception e) {
                if (activeRequests.containsKey(requestId)) {
                    FileLog.e("AI Error: ", e);
                    notifyErrorAndFinish(requestId, callback, 500, e.getMessage() != null ? e.getMessage() : "Unknown error");
                }
            }
        });
        return requestId;
    }

    private void sendStreamChunk(String requestId, String chunk, GenerationCallback callback) {
        if (TextUtils.isEmpty(chunk)) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (activeRequests.containsKey(requestId) && callback != null) {
                callback.onChunk(chunk);
            }
        });
    }

    private void notifyThinking(String requestId, GenerationCallback callback) {
        AndroidUtilities.runOnUIThread(() -> {
            if (activeRequests.containsKey(requestId) && callback != null) {
                callback.onThinking();
            }
        });
    }

    private ArrayList<Message> historyForRequest(ArrayList<Message> history, Backend backend) {
        ArrayList<Message> result = new ArrayList<>();
        int charsLimit = backend.getHistoryCharsLimit();
        int messagesLimit = backend.getHistoryMessagesLimit();
        int totalChars = 0;
        for (int i = history.size() - 1; i > 0 && result.size() < messagesLimit; i--) {
            Message answer = history.get(i);
            if (answer == null || !"assistant".equals(answer.role()) || TextUtils.isEmpty(answer.content())) {
                continue;
            }
            Message question = history.get(i - 1);
            if (question == null || "assistant".equals(question.role()) || TextUtils.isEmpty(question.content())) {
                continue;
            }
            int chars = totalChars + question.content().length() + answer.content().length();
            if (chars > charsLimit || result.size() + 2 > messagesLimit) {
                continue;
            }
            result.add(0, copyTextMessage(question));
            result.add(1, copyTextMessage(answer));
            totalChars = chars;
        }
        return result;
    }

    private void saveTurn(Message message, String response) {
        ArrayList<Message> history = AiConfig.getConversationHistory();
        history.add(copyTextMessage(message));
        history.add(new Message("assistant", response));
        int totalChars = 0;
        for (Message m : history) {
            totalChars += m.content() == null ? 0 : m.content().length();
        }
        while (history.size() > 2 && (history.size() > MAX_HISTORY_MESSAGES || totalChars > MAX_HISTORY_CHARS)) {
            Message removed = history.remove(0);
            totalChars -= removed.content() == null ? 0 : removed.content().length();
        }
        if (history.size() > 2 && "assistant".equals(history.get(0).role())) {
            history.remove(0);
        }
        AiConfig.saveConversationHistory(history);
    }

    private Message copyTextMessage(Message message) {
        return new Message(message.role(), message.content());
    }

    private void notifyResponseAndFinish(String requestId, GenerationCallback callback, String response) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (activeRequests.containsKey(requestId) && callback != null) {
                    callback.onResponse(response);
                }
            } finally {
                finishRequest(requestId);
            }
        });
    }

    private void notifyErrorAndFinish(String requestId, GenerationCallback callback, int code, String error) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (activeRequests.containsKey(requestId) && callback != null) {
                    callback.onError(code, error);
                }
            } finally {
                finishRequest(requestId);
            }
        });
    }

    public boolean isGenerating() {
        return isGenerating.get();
    }

    private void finishRequest(String requestId) {
        activeCancellables.remove(requestId);
        ExecutorService executor = activeRequests.remove(requestId);
        if (executor != null) {
            executor.shutdown();
        }
        if (activeRequests.isEmpty()) {
            isGenerating.set(false);
        }
    }

    public void stopRequest(String requestId) {
        if (TextUtils.isEmpty(requestId)) {
            return;
        }
        Runnable cancel = activeCancellables.remove(requestId);
        if (cancel != null) {
            try {
                cancel.run();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        ExecutorService executor = activeRequests.remove(requestId);
        if (executor != null) {
            executor.shutdownNow();
        }
        if (activeRequests.isEmpty()) {
            isGenerating.set(false);
        }
    }

    public final class RequestSink implements BackendSink {

        private final String requestId;
        private final Message currentMessage;
        private final boolean saveHistory;
        private final GenerationCallback callback;

        public RequestSink(String requestId, Message currentMessage, boolean saveHistory, GenerationCallback callback) {
            this.requestId = requestId;
            this.currentMessage = currentMessage;
            this.saveHistory = saveHistory;
            this.callback = callback;
        }

        @Override
        public boolean isActive() {
            return activeRequests.containsKey(requestId);
        }

        @Override
        public void onCancellable(Runnable cancel) {
            if (cancel != null) {
                activeCancellables.put(requestId, cancel);
            }
        }

        @Override
        public void onThinking() {
            notifyThinking(requestId, callback);
        }

        @Override
        public void onChunk(String chunk) {
            sendStreamChunk(requestId, chunk, callback);
        }

        @Override
        public void onComplete(String response) {
            if (!isActive()) {
                return;
            }
            if (TextUtils.isEmpty(response)) {
                onError(204, "Response body is empty");
                return;
            }
            if (saveHistory) {
                saveTurn(currentMessage, response);
            }
            notifyResponseAndFinish(requestId, callback, response);
        }

        @Override
        public void onError(int code, String error) {
            notifyErrorAndFinish(requestId, callback, code, error);
        }
    }

    public static class Builder {

        private Service serviceOverride;
        private Role roleOverride;

        public Builder serviceOverride(Service service) {
            serviceOverride = service;
            return this;
        }

        public Builder roleOverride(Role role) {
            roleOverride = role;
            return this;
        }

        public Client build() {
            return new Client(this);
        }
    }

    public static final class ImagePayload {

        private final byte[] data;
        private final String mimeType;

        private ImagePayload(byte[] data, String mimeType) {
            this.data = data;
            this.mimeType = mimeType;
        }

        public byte[] data() {
            return data;
        }

        public String mimeType() {
            return mimeType;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ImagePayload)) {
                return false;
            }
            ImagePayload that = (ImagePayload) o;
            return Objects.equals(data, that.data) && Objects.equals(mimeType, that.mimeType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(data, mimeType);
        }

        @NonNull
        @Override
        public String toString() {
            return "ImagePayload[data=" + Arrays.toString(data) + ", mimeType=" + mimeType + "]";
        }
    }
}
