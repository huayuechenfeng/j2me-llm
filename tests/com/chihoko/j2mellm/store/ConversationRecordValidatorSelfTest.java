package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.util.Crc32;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

public final class ConversationRecordValidatorSelfTest {
    private static final int MAGIC = 0x4A324348;
    private static final int VERSION = 2;

    public static void main(String[] args) throws Exception {
        testHeaderAndCrc();
        testWrongHeaderAndLimits();
        testObviousTruncation();
        System.out.println("ConversationRecordValidatorSelfTest passed");
    }

    private static void testHeaderAndCrc() throws Exception {
        byte[] record = record("custom", 1, true);
        require(valid(record, "custom", 24), "valid envelope");
        record[record.length - 8] ^= 0x20;
        require(!valid(record, "custom", 24), "CRC corruption rejected");
    }

    private static void testWrongHeaderAndLimits() throws Exception {
        byte[] record = record("openai", 1, true);
        require(!valid(record, "custom", 24), "profile mismatch");
        require(!ConversationRecordValidator.isValid(record, MAGIC, 3,
                "openai", 24, 262144), "version mismatch");
        require(!valid(record, "openai", 0), "message limit");
        require(!ConversationRecordValidator.isValid(record, MAGIC, VERSION,
                "openai", 24, record.length - 1), "byte limit");
    }

    private static void testObviousTruncation() throws Exception {
        byte[] record = record("custom", 1, false);
        require(!valid(record, "custom", 24), "missing minimum message fields");
    }

    private static boolean valid(byte[] value, String id, int maximumMessages) {
        return ConversationRecordValidator.isValid(value, MAGIC, VERSION,
                id, maximumMessages, 262144);
    }

    private static byte[] record(String id, int count, boolean includeMessage) throws Exception {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream payload = new DataOutputStream(payloadBytes);
        payload.writeInt(MAGIC);
        payload.writeInt(VERSION);
        payload.writeUTF(id);
        payload.writeInt(count);
        if (includeMessage) {
            payload.writeUTF("user");
            payload.writeUTF("hello");
            payload.writeUTF("");
            payload.writeBoolean(false);
            payload.writeUTF("");
            payload.writeUTF("");
            payload.writeUTF("");
        }
        payload.flush();
        byte[] body = payloadBytes.toByteArray();
        ByteArrayOutputStream recordBytes = new ByteArrayOutputStream();
        recordBytes.write(body);
        DataOutputStream record = new DataOutputStream(recordBytes);
        record.writeInt(Crc32.compute(body));
        record.flush();
        return recordBytes.toByteArray();
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new RuntimeException("failed: " + name);
    }
}
