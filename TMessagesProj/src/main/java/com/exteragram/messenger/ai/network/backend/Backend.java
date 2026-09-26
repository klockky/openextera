package com.exteragram.messenger.ai.network.backend;

public interface Backend {

    void execute(BackendRequest request, BackendSink sink) throws Exception;

    int getHistoryCharsLimit();

    int getHistoryMessagesLimit();

    int getMaxOutputTokens();

    float getMaxTemperature();

    boolean supportsImages();
}
