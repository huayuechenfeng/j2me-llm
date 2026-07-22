package com.chihoko.j2mellm.net;

final class ThinkingFilter {
    private static final String OPEN = "<think>";
    private static final String CLOSE = "</think>";

    private final ChatListener listener;
    private String pending = "";
    private boolean thinking;

    ThinkingFilter(ChatListener listener) {
        this.listener = listener;
    }

    void feed(String text) {
        if (text == null || text.length() == 0) return;
        pending += text;
        drain(false);
    }

    void finish() {
        drain(true);
    }

    private void drain(boolean flush) {
        while (pending.length() > 0) {
            String marker = thinking ? CLOSE : OPEN;
            int at = pending.indexOf(marker);
            if (at >= 0) {
                emit(pending.substring(0, at));
                pending = pending.substring(at + marker.length());
                thinking = !thinking;
                continue;
            }

            if (flush) {
                emit(pending);
                pending = "";
                return;
            }

            int keep = marker.length() - 1;
            if (pending.length() <= keep) return;
            int emitLength = pending.length() - keep;
            emit(pending.substring(0, emitLength));
            pending = pending.substring(emitLength);
        }
    }

    private void emit(String value) {
        if (value.length() == 0) return;
        if (thinking) {
            listener.onReasoning(value);
        } else {
            listener.onContent(value);
        }
    }
}

