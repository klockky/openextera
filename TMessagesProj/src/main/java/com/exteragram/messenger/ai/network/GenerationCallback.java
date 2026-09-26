package com.exteragram.messenger.ai.network;

public interface GenerationCallback {

    void onChunk(String chunk);

    void onError(int code, String message);

    void onResponse(String response);

    default void onThinking() {
    }
}
