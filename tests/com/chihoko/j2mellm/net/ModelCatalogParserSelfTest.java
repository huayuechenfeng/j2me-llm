package com.chihoko.j2mellm.net;

import com.chihoko.j2mellm.util.Utf8;

import java.io.IOException;
import java.util.Vector;

public final class ModelCatalogParserSelfTest {
    public static void main(String[] args) throws Exception {
        parsesChunkedEscapesAndNoise();
        enforcesModelAndByteLimits();
        rejectsMalformedResponses();
        testsThinkingMapping();
        System.out.println("ModelCatalogParserSelfTest passed");
    }

    private static void parsesChunkedEscapesAndNoise() throws Exception {
        String source = "{\"object\":\"list\",\"ignored\":{\"data\":[{\"id\":\"wrong\"}]},"
                + "\"data\":[{\"created\":1,\"id\":\"gpt-5\"},{\"id\":\"deep\\u0073eek-测试\","
                + "\"nested\":{\"id\":\"wrong-too\"}},{\"id\":\"gpt-5\"},{\"id\":7}]}";
        byte[] bytes = Utf8.encode(source);
        ModelCatalogParser parser = new ModelCatalogParser(8, 4096);
        int position = 0;
        int[] sizes = new int[] {1, 2, 5, 3, 1, 7};
        int index = 0;
        while (position < bytes.length) {
            int count = sizes[index++ % sizes.length];
            if (count > bytes.length - position) count = bytes.length - position;
            parser.feed(bytes, position, count);
            position += count;
        }
        Vector ids = parser.finish();
        require(ids.size() == 2, "unique string ids only");
        require("gpt-5".equals(ids.elementAt(0)), "first id");
        require("deepseek-测试".equals(ids.elementAt(1)), "escaped and UTF-8 id");
        require(!parser.isTruncated(), "not truncated");
    }

    private static void enforcesModelAndByteLimits() throws Exception {
        ModelCatalogParser parser = new ModelCatalogParser(2, 200);
        feed(parser, "{\"data\":[{\"id\":\"a\"},{\"id\":\"b\"},{\"id\":\"c\"}]}");
        Vector ids = parser.finish();
        require(ids.size() == 2, "model cap");
        require(parser.isTruncated(), "model cap reports truncation");

        final ModelCatalogParser tiny = new ModelCatalogParser(2, 8);
        expectIOException(new CheckedAction() {
            public void run() throws Exception { feed(tiny, "{\"data\":[]}"); }
        }, "byte cap");
    }

    private static void rejectsMalformedResponses() throws Exception {
        final ModelCatalogParser noData = new ModelCatalogParser();
        feed(noData, "{\"models\":[]}");
        expectIOException(new CheckedAction() {
            public void run() throws Exception { noData.finish(); }
        }, "missing data");

        final ModelCatalogParser unfinished = new ModelCatalogParser();
        feed(unfinished, "{\"data\":[{\"id\":\"oops");
        expectIOException(new CheckedAction() {
            public void run() throws Exception { unfinished.finish(); }
        }, "unfinished string");

        expectIOException(new CheckedAction() {
            public void run() throws Exception {
                ModelCatalogParser trailingComma = new ModelCatalogParser();
                feed(trailingComma, "{\"data\":[],}");
                trailingComma.finish();
            }
        }, "trailing comma");
    }

    private static void testsThinkingMapping() {
        StringBuffer json = new StringBuffer("{");
        ThinkingRequestPolicy.appendFields(json, ThinkingRequestPolicy.PROTOCOL_OPENAI_EFFORT,
                ThinkingRequestPolicy.MODE_OFF, "high");
        require("{,\"reasoning_effort\":\"none\"".equals(json.toString()), "OpenAI off");

        json = new StringBuffer("{");
        ThinkingRequestPolicy.appendFields(json, ThinkingRequestPolicy.PROTOCOL_THINKING_OBJECT,
                ThinkingRequestPolicy.MODE_ON, "high");
        require(json.toString().indexOf("\"type\":\"enabled\"") >= 0, "thinking enabled");
        require(json.toString().indexOf("\"reasoning_effort\":\"high\"") >= 0, "thinking effort");

        json = new StringBuffer("{");
        ThinkingRequestPolicy.appendFields(json, ThinkingRequestPolicy.PROTOCOL_ALWAYS_THINKING,
                ThinkingRequestPolicy.MODE_OFF, "low");
        require("{".equals(json.toString()), "always-thinking off emits no invalid field");
        require(ThinkingRequestPolicy.effectiveMode(ThinkingRequestPolicy.PROTOCOL_ALWAYS_THINKING,
                ThinkingRequestPolicy.MODE_OFF) == ThinkingRequestPolicy.MODE_ON, "always-thinking effective mode");
        require(!ThinkingRequestPolicy.canDisable(ThinkingRequestPolicy.PROTOCOL_ALWAYS_THINKING),
                "always-thinking cannot disable");
    }

    private static void feed(ModelCatalogParser parser, String value) throws IOException {
        byte[] bytes = Utf8.encode(value);
        parser.feed(bytes, 0, bytes.length);
    }

    private static void expectIOException(CheckedAction action, String name) throws Exception {
        try {
            action.run();
            throw new RuntimeException("failed: " + name + " did not throw");
        } catch (IOException expected) { }
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new RuntimeException("failed: " + name);
    }

    private interface CheckedAction {
        void run() throws Exception;
    }
}
