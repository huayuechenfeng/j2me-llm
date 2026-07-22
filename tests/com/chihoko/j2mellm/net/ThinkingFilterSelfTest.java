package com.chihoko.j2mellm.net;

public final class ThinkingFilterSelfTest {
    public static void main(String[] args) {
        Capture capture = new Capture();
        ThinkingFilter filter = new ThinkingFilter(capture);
        filter.feed("回答前<th");
        filter.feed("ink>内部思考</thi");
        filter.feed("nk>最终答案");
        filter.finish();
        require("回答前最终答案".equals(capture.content.toString()), "content");
        require("内部思考".equals(capture.reasoning.toString()), "reasoning");
        System.out.println("ThinkingFilterSelfTest passed");
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new RuntimeException("failed: " + name);
    }

    private static final class Capture implements ChatListener {
        final StringBuffer content = new StringBuffer();
        final StringBuffer reasoning = new StringBuffer();

        public void onContent(String text) { content.append(text); }
        public void onReasoning(String text) { reasoning.append(text); }
        public void onImage(String source) { }
        public void onComplete() { }
        public void onError(String message) { throw new RuntimeException(message); }
    }
}

