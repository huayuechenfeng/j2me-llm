package com.chihoko.j2mellm.model;

/** Device-side soft resource limits. Values may be unlocked by the user. */
public final class ResourceLimits {
    public static final int MODE_COMPATIBLE = 0;
    public static final int MODE_RECOMMENDED = 1;
    public static final int MODE_CUSTOM = 2;
    public static final int IMAGE_COMPATIBLE = 0;
    public static final int IMAGE_HIGH_PERFORMANCE = 1;
    public static final int IMAGE_CUSTOM = 2;

    public int mode;
    public int activeConversationChars;
    public int activeMessages;
    public int messageContentChars;
    public int messageReasoningChars;
    public int savedMessages;
    public int requestContextChars;
    public int searchContextChars;
    public int searchResults;
    public int imageMode;
    public int maximumInputImageBytes;
    public int maximumImagePixels;
    public int maximumReturnedImageBytes;

    public static ResourceLimits compatible() {
        ResourceLimits value = new ResourceLimits();
        value.mode = MODE_COMPATIBLE;
        value.activeConversationChars = 49152;
        value.activeMessages = 32;
        value.messageContentChars = 24576;
        value.messageReasoningChars = 8192;
        value.savedMessages = 24;
        value.requestContextChars = 49152;
        value.searchContextChars = 4000;
        value.searchResults = 3;
        applyCompatibleImages(value);
        return value;
    }

    public static ResourceLimits recommended() {
        ResourceLimits value = new ResourceLimits();
        value.mode = MODE_RECOMMENDED;
        value.activeConversationChars = 131072;
        value.activeMessages = 64;
        value.messageContentChars = 49152;
        value.messageReasoningChars = 16384;
        value.savedMessages = 64;
        value.requestContextChars = 96000;
        value.searchContextChars = 6000;
        value.searchResults = 5;
        applyCompatibleImages(value);
        return value;
    }

    public ResourceLimits copy() {
        ResourceLimits copy = new ResourceLimits();
        copy.mode = mode;
        copy.activeConversationChars = activeConversationChars;
        copy.activeMessages = activeMessages;
        copy.messageContentChars = messageContentChars;
        copy.messageReasoningChars = messageReasoningChars;
        copy.savedMessages = savedMessages;
        copy.requestContextChars = requestContextChars;
        copy.searchContextChars = searchContextChars;
        copy.searchResults = searchResults;
        copy.imageMode = imageMode;
        copy.maximumInputImageBytes = maximumInputImageBytes;
        copy.maximumImagePixels = maximumImagePixels;
        copy.maximumReturnedImageBytes = maximumReturnedImageBytes;
        return copy;
    }

    public void normalize() {
        if (mode < MODE_COMPATIBLE || mode > MODE_CUSTOM) mode = MODE_RECOMMENDED;
        activeConversationChars = range(activeConversationChars, 32768, 1048576, 131072);
        activeMessages = range(activeMessages, 16, 256, 64);
        messageContentChars = range(messageContentChars, 8192, 262144, 49152);
        messageReasoningChars = range(messageReasoningChars, 4096, 131072, 16384);
        savedMessages = range(savedMessages, 16, 256, 64);
        requestContextChars = range(requestContextChars, 16384, 1048576, 96000);
        searchContextChars = range(searchContextChars, 1000, 32000, 6000);
        searchResults = range(searchResults, 1, SearchBundle.MAX_RESULTS, 5);
        if (imageMode < IMAGE_COMPATIBLE || imageMode > IMAGE_CUSTOM) {
            imageMode = IMAGE_COMPATIBLE;
        }
        maximumInputImageBytes = range(maximumInputImageBytes,
                32768, 4194304, 98304);
        maximumImagePixels = range(maximumImagePixels,
                65536, 4194304, 65536);
        maximumReturnedImageBytes = range(maximumReturnedImageBytes,
                65536, 8388608, 262144);
    }

    public static void applyCompatibleImages(ResourceLimits value) {
        value.imageMode = IMAGE_COMPATIBLE;
        value.maximumInputImageBytes = 98304;
        value.maximumImagePixels = 65536;
        value.maximumReturnedImageBytes = 262144;
    }

    public static void applyHighPerformanceImages(ResourceLimits value) {
        value.imageMode = IMAGE_HIGH_PERFORMANCE;
        value.maximumInputImageBytes = 524288;
        value.maximumImagePixels = 1048576;
        value.maximumReturnedImageBytes = 1048576;
    }

    private int range(int value, int minimum, int maximum, int fallback) {
        if (value < minimum || value > maximum) return fallback;
        return value;
    }
}
