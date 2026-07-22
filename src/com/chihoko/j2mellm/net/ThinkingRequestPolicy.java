package com.chihoko.j2mellm.net;

import com.chihoko.j2mellm.util.Json;

/** Small provider-independent mapper for OpenAI-compatible thinking fields. */
public final class ThinkingRequestPolicy {
    public static final int MODE_AUTO = 0;
    public static final int MODE_ON = 1;
    public static final int MODE_OFF = 2;

    public static final int PROTOCOL_NONE = 0;
    public static final int PROTOCOL_OPENAI_EFFORT = 1;
    public static final int PROTOCOL_THINKING_OBJECT = 2;
    public static final int PROTOCOL_ALWAYS_THINKING = 3;

    private ThinkingRequestPolicy() {
    }

    public static boolean canDisable(int protocol) {
        return protocol == PROTOCOL_OPENAI_EFFORT || protocol == PROTOCOL_THINKING_OBJECT
                || protocol == PROTOCOL_NONE;
    }

    public static int effectiveMode(int protocol, int requestedMode) {
        int mode = validMode(requestedMode) ? requestedMode : MODE_AUTO;
        if (protocol == PROTOCOL_ALWAYS_THINKING && mode == MODE_OFF) return MODE_ON;
        return mode;
    }

    /** Appends comma-prefixed request fields to an existing root JSON object. */
    public static void appendFields(StringBuffer json, int protocol, int mode, String effort) {
        if (json == null) throw new NullPointerException("json");
        if (!validMode(mode) || mode == MODE_AUTO || protocol == PROTOCOL_NONE) return;
        if (protocol == PROTOCOL_OPENAI_EFFORT) {
            appendEffort(json, mode == MODE_OFF ? "none" : defaultEffort(effort));
        } else if (protocol == PROTOCOL_THINKING_OBJECT) {
            json.append(",\"thinking\":{\"type\":")
                    .append(Json.quote(mode == MODE_OFF ? "disabled" : "enabled")).append('}');
            if (mode == MODE_ON && supportedEffort(effort)) appendEffort(json, effort);
        } else if (protocol == PROTOCOL_ALWAYS_THINKING && mode == MODE_ON) {
            appendEffort(json, defaultEffort(effort));
        }
    }

    private static void appendEffort(StringBuffer json, String effort) {
        json.append(",\"reasoning_effort\":").append(Json.quote(effort));
    }

    private static String defaultEffort(String effort) {
        return supportedEffort(effort) ? effort : "low";
    }

    public static boolean supportedEffort(String effort) {
        return "minimal".equals(effort) || "low".equals(effort)
                || "medium".equals(effort) || "high".equals(effort)
                || "xhigh".equals(effort) || "max".equals(effort);
    }

    private static boolean validMode(int mode) {
        return mode == MODE_AUTO || mode == MODE_ON || mode == MODE_OFF;
    }
}
