package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.util.Crc32;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

/** Validates only a conversation envelope, without decoding or copying messages. */
public final class ConversationRecordValidator {
    private static final int MIN_MESSAGE_BYTES = 13;

    private ConversationRecordValidator() {
    }

    public static boolean isValid(byte[] record, int expectedMagic, int expectedVersion,
            String expectedProfileId, int maximumMessages, int maximumBytes) {
        if (record == null || expectedProfileId == null || maximumMessages < 0
                || record.length < 18 || record.length > maximumBytes) return false;
        int payloadLength = record.length - 4;
        if (readInt(record, payloadLength) != Crc32.compute(record, 0, payloadLength)) return false;
        try {
            DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(record, 0, payloadLength));
            if (input.readInt() != expectedMagic || input.readInt() != expectedVersion) return false;
            if (!expectedProfileId.equals(input.readUTF())) return false;
            int count = input.readInt();
            if (count < 0 || count > maximumMessages) return false;
            return input.available() >= count * MIN_MESSAGE_BYTES;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int readInt(byte[] value, int offset) {
        return ((value[offset] & 0xff) << 24)
                | ((value[offset + 1] & 0xff) << 16)
                | ((value[offset + 2] & 0xff) << 8)
                | (value[offset + 3] & 0xff);
    }
}
