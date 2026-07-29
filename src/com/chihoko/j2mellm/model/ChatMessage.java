package com.chihoko.j2mellm.model;

import javax.microedition.lcdui.Image;

public final class ChatMessage {
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    private static int maximumContentChars = 49152;
    private static int maximumReasoningChars = 16384;

    public final String role;
    private final StringBuffer content;
    private final StringBuffer reasoning;
    private MessageMedia media;
    private SearchBundle searchBundle;
    private int revision;
    public boolean pending;
    public boolean error;

    public ChatMessage(String role, String text) {
        this.role = role;
        this.content = new StringBuffer();
        this.reasoning = new StringBuffer();
        appendLimited(this.content, text, maximumContentChars);
    }

    public synchronized void appendContent(String text) {
        if (appendLimited(content, text, maximumContentChars)) revision++;
    }

    public synchronized void appendReasoning(String text) {
        if (appendLimited(reasoning, text, maximumReasoningChars)) revision++;
    }

    public synchronized void replaceContent(String text) {
        content.setLength(0);
        appendLimited(content, text, maximumContentChars);
        revision++;
    }

    public synchronized String getContent() {
        return content.toString();
    }

    public synchronized String getReasoning() {
        return reasoning.toString();
    }

    public synchronized boolean hasReasoning() {
        return reasoning.length() > 0;
    }

    public synchronized int getCharacterCost() {
        int value = content.length() + reasoning.length();
        if (searchBundle != null) value += searchBundle.getCharacterCost();
        return value;
    }

    public synchronized void setSearchBundle(SearchBundle value) {
        searchBundle = value;
        revision++;
    }

    public synchronized SearchBundle getSearchBundle() {
        return searchBundle;
    }

    public static synchronized void configureLimits(ResourceLimits limits) {
        ResourceLimits value = limits == null ? ResourceLimits.recommended() : limits;
        value.normalize();
        maximumContentChars = value.messageContentChars;
        maximumReasoningChars = value.messageReasoningChars;
    }

    public synchronized int getRevision() {
        int state = revision;
        if (pending) state += 0x20000000;
        if (error) state += 0x40000000;
        return state;
    }

    public synchronized void setAttachment(ImageAttachment attachment) {
        if (attachment == null) return;
        MessageMedia value = media();
        value.name = attachment.name;
        value.mimeType = attachment.mimeType;
        value.data = attachment.data;
        revision++;
    }

    public synchronized String getImageName() {
        return media == null ? "" : media.name;
    }

    public synchronized String getImageMime() {
        return media == null ? "" : media.mimeType;
    }

    public synchronized byte[] getImageData() {
        return media == null ? null : media.data;
    }

    public synchronized void releaseImageData() {
        if (media != null) media.data = null;
    }

    public synchronized void setImageMetadata(String name, String mime) {
        if ((name == null || name.length() == 0) && (mime == null || mime.length() == 0)) return;
        MessageMedia value = media();
        value.name = name == null ? "" : name;
        value.mimeType = mime == null ? "" : mime;
        revision++;
    }

    public synchronized void setImageSource(String source) {
        if (source == null || source.length() == 0) return;
        MessageMedia value = media();
        if (value.source.length() == 0) {
            value.source = source;
            revision++;
        }
    }

    public synchronized String getImageSource() {
        return media == null ? "" : media.source;
    }

    public synchronized void releaseInlineImageSource() {
        if (media != null && media.source.startsWith("data:image/")) {
            media.source = "";
            revision++;
        }
    }

    public synchronized void setImageStatus(String status) {
        String value = status == null ? "" : status;
        if (media == null && value.length() == 0) return;
        MessageMedia target = media();
        target.status = value;
        revision++;
    }

    public synchronized String getImageStatus() {
        return media == null ? "" : media.status;
    }

    public synchronized void setImagePreview(Image image) {
        if (media == null && image == null) return;
        media().preview = image;
        revision++;
    }

    public synchronized Image getImagePreview() {
        return media == null ? null : media.preview;
    }

    public synchronized boolean hasMedia() {
        return media != null && (media.name.length() > 0 || media.source.length() > 0
                || media.preview != null || media.data != null);
    }

    public synchronized void releaseMediaPreview() {
        if (media != null) {
            media.preview = null;
            revision++;
        }
    }

    private MessageMedia media() {
        if (media == null) media = new MessageMedia();
        return media;
    }

    private boolean appendLimited(StringBuffer target, String value, int limit) {
        if (value == null || value.length() == 0 || target.length() >= limit) return false;
        int remaining = limit - target.length();
        if (value.length() <= remaining) target.append(value);
        else target.append(value.substring(0, remaining));
        return true;
    }
}
