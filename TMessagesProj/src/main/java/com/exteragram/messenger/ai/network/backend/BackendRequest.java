package com.exteragram.messenger.ai.network.backend;

import androidx.annotation.NonNull;

import com.exteragram.messenger.ai.data.Message;
import com.exteragram.messenger.ai.data.Role;
import com.exteragram.messenger.ai.data.Service;

import java.util.List;
import java.util.Objects;

public final class BackendRequest {

    private final Service service;
    private final Role role;
    private final List<Message> history;
    private final Message currentMessage;
    private final boolean stream;
    private final float temperature;
    private final int maxOutputTokens;

    public BackendRequest(Service service, Role role, List<Message> history, Message currentMessage, boolean stream, float temperature, int maxOutputTokens) {
        this.service = service;
        this.role = role;
        this.history = history;
        this.currentMessage = currentMessage;
        this.stream = stream;
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
    }

    public Service service() {
        return service;
    }

    public Role role() {
        return role;
    }

    public List<Message> history() {
        return history;
    }

    public Message currentMessage() {
        return currentMessage;
    }

    public boolean stream() {
        return stream;
    }

    public float temperature() {
        return temperature;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BackendRequest)) {
            return false;
        }
        BackendRequest that = (BackendRequest) o;
        return stream == that.stream && maxOutputTokens == that.maxOutputTokens && temperature == that.temperature
                && Objects.equals(service, that.service) && Objects.equals(role, that.role)
                && Objects.equals(history, that.history) && Objects.equals(currentMessage, that.currentMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(service, role, history, currentMessage, stream, temperature, maxOutputTokens);
    }

    @NonNull
    @Override
    public String toString() {
        return "BackendRequest[service=" + service + ", role=" + role + ", history=" + history + ", currentMessage=" + currentMessage
                + ", stream=" + stream + ", temperature=" + temperature + ", maxOutputTokens=" + maxOutputTokens + "]";
    }
}
