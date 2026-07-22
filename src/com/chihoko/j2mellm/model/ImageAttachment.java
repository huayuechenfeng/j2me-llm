package com.chihoko.j2mellm.model;

public final class ImageAttachment {
    public final String name;
    public final String mimeType;
    public final byte[] data;

    public ImageAttachment(String name, String mimeType, byte[] data) {
        this.name = name == null ? "image" : name;
        this.mimeType = mimeType == null ? "image/jpeg" : mimeType;
        this.data = data;
    }
}

