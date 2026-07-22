package com.chihoko.j2mellm.util;

import java.io.ByteArrayOutputStream;

public final class JsonStreamWriterSelfTest {
    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonStreamWriter output = new JsonStreamWriter(bytes);
        writeSample(output);
        output.finish();

        JsonStreamWriter counter = new JsonStreamWriter(null);
        writeSample(counter);
        counter.finish();
        require(counter.size() == bytes.size(), "two-pass byte count");
        String actual = Utf8.decode(bytes.toByteArray());
        String expected = "{\"text\":\"中文\\n\\\"\\\\😀\",\"data\":\"AQIDBA==\"}";
        require(expected.equals(actual), "UTF-8, escaping and streaming base64");
        System.out.println("JsonStreamWriterSelfTest passed");
    }

    private static void writeSample(JsonStreamWriter writer) throws Exception {
        writer.raw("{\"text\":");
        writer.quoted("中文\n\"\\😀");
        writer.raw(",\"data\":\"");
        writer.base64(new byte[] {1, 2, 3, 4});
        writer.raw("\"}");
    }

    private static void require(boolean value, String label) {
        if (!value) throw new RuntimeException("failed: " + label);
    }
}
