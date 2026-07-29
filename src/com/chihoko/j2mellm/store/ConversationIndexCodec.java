package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ConversationMeta;
import com.chihoko.j2mellm.model.ConversationState;
import com.chihoko.j2mellm.util.Crc32;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** RMS-independent v0.4 conversation index codec. */
public final class ConversationIndexCodec {
    private static final int MAGIC = 0x4A324349;
    private static final int VERSION = 1;
    private static final int MAX_CONVERSATIONS = 64;
    private static final int MAX_BYTES = 65536;

    private ConversationIndexCodec() {
    }

    public static byte[] encode(ConversationState state) throws IOException {
        if (state == null) throw new IOException("Missing conversation state");
        if (state.conversations.size() > MAX_CONVERSATIONS) {
            throw new IOException("Too many conversations");
        }
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(payloadBytes);
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        output.writeUTF(limitedId(state.activeConversationId));
        output.writeInt(state.conversations.size());
        int i;
        for (i = 0; i < state.conversations.size(); i++) {
            ConversationMeta value = (ConversationMeta) state.conversations.elementAt(i);
            output.writeUTF(limitedId(value.id));
            output.writeUTF(limited(value.profileId, 32, "profile id"));
            output.writeUTF(limited(value.title, ConversationMeta.MAX_TITLE_CHARS, "title"));
            output.writeUTF(limited(value.preview, ConversationMeta.MAX_PREVIEW_CHARS, "preview"));
            output.writeLong(value.createdAt);
            output.writeLong(value.updatedAt);
            output.writeInt(value.messageCount);
        }
        output.flush();
        byte[] payload = payloadBytes.toByteArray();
        output.close();

        ByteArrayOutputStream recordBytes = new ByteArrayOutputStream(payload.length + 4);
        recordBytes.write(payload);
        DataOutputStream record = new DataOutputStream(recordBytes);
        record.writeInt(Crc32.compute(payload, 0, payload.length));
        record.flush();
        byte[] encoded = recordBytes.toByteArray();
        record.close();
        if (encoded.length > MAX_BYTES) throw new IOException("Conversation index is too large");
        return encoded;
    }

    public static ConversationState decode(byte[] record) throws IOException {
        if (!isValidRecord(record)) throw new IOException("Invalid conversation index");
        int payloadLength = record.length - 4;
        DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(record, 0, payloadLength));
        if (input.readInt() != MAGIC || input.readInt() != VERSION) {
            throw new IOException("Unsupported conversation index");
        }
        ConversationState state = new ConversationState();
        state.activeConversationId = checkedId(input.readUTF());
        int count = input.readInt();
        if (count < 0 || count > MAX_CONVERSATIONS) throw new IOException("Invalid conversation count");
        int i;
        for (i = 0; i < count; i++) {
            String id = checkedId(input.readUTF());
            if (id.length() == 0 || state.find(id) != null) {
                throw new IOException("Invalid conversation id");
            }
            ConversationMeta value = new ConversationMeta(id,
                    limitedRead(input.readUTF(), 32, "profile id"));
            value.title = limitedRead(input.readUTF(), ConversationMeta.MAX_TITLE_CHARS, "title");
            value.preview = limitedRead(input.readUTF(), ConversationMeta.MAX_PREVIEW_CHARS, "preview");
            value.createdAt = input.readLong();
            value.updatedAt = input.readLong();
            value.messageCount = input.readInt();
            if (value.messageCount < 0) throw new IOException("Invalid message count");
            state.conversations.addElement(value);
        }
        if (state.find(state.activeConversationId) == null) {
            state.activeConversationId = state.conversations.size() == 0 ? ""
                    : ((ConversationMeta) state.conversations.elementAt(0)).id;
        }
        return state;
    }

    public static boolean isValidRecord(byte[] record) {
        if (record == null || record.length < 16 || record.length > MAX_BYTES) return false;
        int payloadLength = record.length - 4;
        return readInt(record, 0) == MAGIC && readInt(record, 4) == VERSION
                && readInt(record, payloadLength) == Crc32.compute(record, 0, payloadLength);
    }

    private static String limitedId(String value) throws IOException {
        value = limited(value, ConversationMeta.MAX_ID_CHARS, "conversation id");
        return checkedId(value);
    }

    private static String checkedId(String value) throws IOException {
        if (value == null) return "";
        int i;
        for (i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-')) {
                throw new IOException("Unsafe conversation id");
            }
        }
        if (value.length() > ConversationMeta.MAX_ID_CHARS) {
            throw new IOException("Conversation id is too long");
        }
        return value;
    }

    private static String limited(String value, int maximum, String field) throws IOException {
        if (value == null) return "";
        if (value.length() > maximum) throw new IOException(field + " is too long");
        return value;
    }

    private static String limitedRead(String value, int maximum, String field) throws IOException {
        return limited(value, maximum, field);
    }

    private static int readInt(byte[] value, int offset) {
        return ((value[offset] & 0xff) << 24)
                | ((value[offset + 1] & 0xff) << 16)
                | ((value[offset + 2] & 0xff) << 8)
                | (value[offset + 3] & 0xff);
    }
}
