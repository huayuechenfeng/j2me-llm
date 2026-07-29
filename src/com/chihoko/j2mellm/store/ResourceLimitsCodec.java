package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ResourceLimits;
import com.chihoko.j2mellm.util.Crc32;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Small versioned codec for resource-limit preferences. */
public final class ResourceLimitsCodec {
    private static final int MAGIC = 0x4A324C4D;
    private static final int VERSION = 2;

    private ResourceLimitsCodec() {
    }

    public static byte[] encode(ResourceLimits source) throws IOException {
        ResourceLimits value = source == null ? ResourceLimits.recommended() : source.copy();
        value.normalize();
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(payloadBytes);
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        output.writeInt(value.mode);
        output.writeInt(value.activeConversationChars);
        output.writeInt(value.activeMessages);
        output.writeInt(value.messageContentChars);
        output.writeInt(value.messageReasoningChars);
        output.writeInt(value.savedMessages);
        output.writeInt(value.requestContextChars);
        output.writeInt(value.searchContextChars);
        output.writeInt(value.searchResults);
        output.writeInt(value.imageMode);
        output.writeInt(value.maximumInputImageBytes);
        output.writeInt(value.maximumImagePixels);
        output.writeInt(value.maximumReturnedImageBytes);
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
        return encoded;
    }

    public static ResourceLimits decode(byte[] record) throws IOException {
        if (record == null || (record.length != 48 && record.length != 64)) {
            throw new IOException("Invalid limits record");
        }
        int payloadLength = record.length - 4;
        if (readInt(record, payloadLength) != Crc32.compute(record, 0, payloadLength)) {
            throw new IOException("Limits checksum mismatch");
        }
        DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(record, 0, payloadLength));
        if (input.readInt() != MAGIC) {
            throw new IOException("Unsupported limits record");
        }
        int version = input.readInt();
        if (version != 1 && version != VERSION) throw new IOException("Unsupported limits record");
        ResourceLimits value = new ResourceLimits();
        value.mode = input.readInt();
        value.activeConversationChars = input.readInt();
        value.activeMessages = input.readInt();
        value.messageContentChars = input.readInt();
        value.messageReasoningChars = input.readInt();
        value.savedMessages = input.readInt();
        value.requestContextChars = input.readInt();
        value.searchContextChars = input.readInt();
        value.searchResults = input.readInt();
        if (version >= 2) {
            value.imageMode = input.readInt();
            value.maximumInputImageBytes = input.readInt();
            value.maximumImagePixels = input.readInt();
            value.maximumReturnedImageBytes = input.readInt();
        } else {
            ResourceLimits.applyCompatibleImages(value);
        }
        value.normalize();
        return value;
    }

    private static int readInt(byte[] value, int offset) {
        return ((value[offset] & 0xff) << 24)
                | ((value[offset + 1] & 0xff) << 16)
                | ((value[offset + 2] & 0xff) << 8)
                | (value[offset + 3] & 0xff);
    }
}
