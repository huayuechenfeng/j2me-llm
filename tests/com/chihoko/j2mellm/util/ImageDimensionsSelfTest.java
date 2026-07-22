package com.chihoko.j2mellm.util;

public final class ImageDimensionsSelfTest {
    public static void main(String[] args) {
        testPngAndPixelLimit();
        testGif();
        testJpegWithLeadingSegment();
        testWebpVariants();
        testUnknownAndTruncated();
        System.out.println("ImageDimensionsSelfTest passed");
    }

    private static void testPngAndPixelLimit() {
        byte[] png = new byte[24];
        int[] signature = new int[] {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        int i;
        for (i = 0; i < signature.length; i++) png[i] = (byte) signature[i];
        write32(png, 8, 13);
        png[12] = 0x49; png[13] = 0x48; png[14] = 0x44; png[15] = 0x52;
        write32(png, 16, 320);
        write32(png, 20, 200);
        ImageDimensions value = ImageDimensions.parse(png);
        require(value != null && value.width == 320 && value.height == 200, "PNG dimensions");
        require(ImageDimensions.PNG.equals(value.format), "PNG format");
        require(value.fitsPixelLimit(65536), "64000 pixels accepted");
        write32(png, 16, 257);
        write32(png, 20, 256);
        value = ImageDimensions.parse(png);
        require(value != null && !value.fitsPixelLimit(65536), "over-limit PNG rejected");
    }

    private static void testGif() {
        byte[] gif = new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x02, 0x00, 0x03, 0x00};
        ImageDimensions value = ImageDimensions.parse(gif);
        require(value != null && value.width == 2 && value.height == 3, "GIF dimensions");
    }

    private static void testJpegWithLeadingSegment() {
        byte[] jpeg = new byte[] {
            (byte) 0xff, (byte) 0xd8,
            (byte) 0xff, (byte) 0xe0, 0x00, 0x04, 0x00, 0x00,
            (byte) 0xff, (byte) 0xc0, 0x00, 0x0b, 0x08,
            0x01, 0x2c, 0x01, (byte) 0x90, 0x01, 0x00, 0x00, 0x00
        };
        ImageDimensions value = ImageDimensions.parse(jpeg);
        require(value != null && value.width == 400 && value.height == 300, "JPEG dimensions");
    }

    private static void testWebpVariants() {
        byte[] webp = webpHeader("VP8X", 30);
        write24Little(webp, 24, 639);
        write24Little(webp, 27, 479);
        ImageDimensions value = ImageDimensions.parse(webp);
        require(value != null && value.width == 640 && value.height == 480, "VP8X dimensions");

        webp = webpHeader("VP8L", 25);
        webp[20] = 0x2f;
        int widthMinusOne = 31;
        int heightMinusOne = 15;
        webp[21] = (byte) (widthMinusOne & 0xff);
        webp[22] = (byte) (((widthMinusOne >> 8) & 0x3f) | ((heightMinusOne & 3) << 6));
        webp[23] = (byte) ((heightMinusOne >> 2) & 0xff);
        webp[24] = (byte) ((heightMinusOne >> 10) & 0x0f);
        value = ImageDimensions.parse(webp);
        require(value != null && value.width == 32 && value.height == 16, "VP8L dimensions");
    }

    private static void testUnknownAndTruncated() {
        require(ImageDimensions.parse(new byte[] {1, 2, 3}) == null, "unknown format");
        require(ImageDimensions.parse(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}) == null,
                "truncated JPEG");
    }

    private static byte[] webpHeader(String type, int length) {
        byte[] data = new byte[length];
        data[0] = 0x52; data[1] = 0x49; data[2] = 0x46; data[3] = 0x46;
        data[8] = 0x57; data[9] = 0x45; data[10] = 0x42; data[11] = 0x50;
        int i;
        for (i = 0; i < 4; i++) data[12 + i] = (byte) type.charAt(i);
        return data;
    }

    private static void write32(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >> 24);
        data[offset + 1] = (byte) (value >> 16);
        data[offset + 2] = (byte) (value >> 8);
        data[offset + 3] = (byte) value;
    }

    private static void write24Little(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
        data[offset + 2] = (byte) (value >> 16);
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new RuntimeException("failed: " + name);
    }
}
