package com.chihoko.j2mellm.model;

/** Lightweight metadata for a persisted conversation. */
public final class ConversationMeta {
    public static final int MAX_ID_CHARS = 24;
    public static final int MAX_TITLE_CHARS = 64;
    public static final int MAX_PREVIEW_CHARS = 96;

    public String id;
    public String profileId;
    public String title;
    public String preview;
    public long createdAt;
    public long updatedAt;
    public int messageCount;

    public ConversationMeta(String conversationId, String providerProfileId) {
        id = safe(conversationId);
        profileId = safe(providerProfileId);
        title = "";
        preview = "";
        createdAt = System.currentTimeMillis();
        updatedAt = createdAt;
    }

    public ConversationMeta copy() {
        ConversationMeta copy = new ConversationMeta(id, profileId);
        copy.title = safe(title);
        copy.preview = safe(preview);
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        copy.messageCount = messageCount;
        return copy;
    }

    public String displayTitle() {
        String value = title == null ? "" : title.trim();
        return value.length() == 0 ? "New chat" : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
