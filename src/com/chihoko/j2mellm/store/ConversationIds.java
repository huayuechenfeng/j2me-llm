package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ConversationState;

/** Generates compact RMS-safe conversation identifiers. */
public final class ConversationIds {
    private static int sequence;

    private ConversationIds() {
    }

    public static synchronized String next(ConversationState state) {
        int attempts;
        for (attempts = 0; attempts < 1000; attempts++) {
            long now = System.currentTimeMillis();
            sequence = (sequence + 1) & 0xffff;
            String value = Long.toString(now, 36) + Integer.toString(sequence, 36);
            if (value.length() > 20) value = value.substring(value.length() - 20);
            if (state == null || state.find(value) == null) return value;
        }
        return Long.toString(System.currentTimeMillis(), 36);
    }
}
