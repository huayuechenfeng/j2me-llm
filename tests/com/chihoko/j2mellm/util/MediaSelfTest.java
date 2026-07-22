package com.chihoko.j2mellm.util;

public final class MediaSelfTest {
    public static void main(String[] args) throws Exception {
        byte[] source = new byte[] {0, 1, 2, 3, 126, 127, -1};
        String encoded = Base64.encode(source);
        byte[] decoded = Base64.decode(encoded);
        require(decoded.length == source.length, "base64 length");
        int i;
        for (i = 0; i < source.length; i++) require(decoded[i] == source[i], "base64 byte " + i);
        require("https://example.com/a.png".equals(
                ImageReferenceParser.firstImageSource("结果：![图](https://example.com/a.png)")), "markdown");
        require("data:image/png;base64,AA==".equals(
                ImageReferenceParser.firstImageSource("data:image/png;base64,AA==\n")), "data url");
        System.out.println("MediaSelfTest passed");
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new RuntimeException("failed: " + name);
    }
}
