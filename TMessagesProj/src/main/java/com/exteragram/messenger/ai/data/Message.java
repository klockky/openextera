package com.exteragram.messenger.ai.data;

import java.util.Objects;

public final class Message {

    private final String role;
    private final String content;
    private byte[] imageData;
    private String mimeType;

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public Message(String role, String content, byte[] imageData, String mimeType) {
        this.role = role;
        this.content = content;
        this.imageData = imageData;
        this.mimeType = mimeType;
    }

    public String role() {
        return role;
    }

    public String content() {
        return content;
    }

    public byte[] getImageData() {
        return imageData;
    }

    public String getMimeType() {
        return mimeType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return content.equals(((Message) o).content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content);
    }
}
