package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ResourceLimits;

import java.io.IOException;

public final class ResourceLimitsCodecSelfTest {
    public static void main(String[] args) throws Exception {
        ResourceLimits source = ResourceLimits.recommended();
        source.mode = ResourceLimits.MODE_CUSTOM;
        source.activeConversationChars = 524288;
        source.requestContextChars = 400000;
        source.imageMode = ResourceLimits.IMAGE_CUSTOM;
        source.maximumInputImageBytes = 2097152;
        source.maximumImagePixels = 3145728;
        source.maximumReturnedImageBytes = 4194304;

        byte[] encoded = ResourceLimitsCodec.encode(source);
        ResourceLimits restored = ResourceLimitsCodec.decode(encoded);
        require(restored.mode == ResourceLimits.MODE_CUSTOM, "custom mode");
        require(restored.requestContextChars == 400000, "request context");
        require(restored.maximumInputImageBytes == 2097152, "input image bytes");
        require(restored.maximumImagePixels == 3145728, "image pixels");
        require(restored.maximumReturnedImageBytes == 4194304, "returned image bytes");
        encoded[12] ^= 1;
        boolean rejected = false;
        try { ResourceLimitsCodec.decode(encoded); }
        catch (IOException expected) { rejected = true; }
        require(rejected, "corruption detected");
        System.out.println("ResourceLimitsCodecSelfTest passed");
    }

    private static void require(boolean value, String label) {
        if (!value) throw new RuntimeException("failed: " + label);
    }
}
