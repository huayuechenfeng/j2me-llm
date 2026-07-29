package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.SearchConfig;
import com.chihoko.j2mellm.util.Crc32;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Versioned, checksummed search preference record. */
final class SearchConfigCodec {
    private static final int MAGIC = 0x4A325343;
    private static final int VERSION = 1;
    private static final int MAX_BYTES = 4096;

    private SearchConfigCodec() {
    }

    static byte[] encode(SearchConfig source) throws IOException {
        SearchConfig value = source == null ? new SearchConfig() : source.copy();
        value.normalize();
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(payloadBytes);
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        output.writeBoolean(value.enabled);
        output.writeUTF(value.presetId);
        output.writeUTF(value.endpoint);
        output.writeUTF(value.apiKey);
        output.writeInt(value.maximumResults);
        output.flush();
        byte[] payload = payloadBytes.toByteArray();
        output.close();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(payload.length + 4);
        bytes.write(payload);
        DataOutputStream result = new DataOutputStream(bytes);
        result.writeInt(Crc32.compute(payload, 0, payload.length));
        result.flush();
        byte[] encoded = bytes.toByteArray();
        result.close();
        if (encoded.length > MAX_BYTES) throw new IOException("Search settings are too large");
        return encoded;
    }

    static SearchConfig decode(byte[] record) throws IOException {
        if (record == null || record.length < 17 || record.length > MAX_BYTES) {
            throw new IOException("Invalid search settings");
        }
        int payloadLength = record.length - 4;
        if (readInt(record, payloadLength) != Crc32.compute(record, 0, payloadLength)) {
            throw new IOException("Search settings checksum mismatch");
        }
        DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(record, 0, payloadLength));
        if (input.readInt() != MAGIC || input.readInt() != VERSION) {
            throw new IOException("Unsupported search settings");
        }
        SearchConfig value = new SearchConfig();
        value.enabled = input.readBoolean();
        value.presetId = input.readUTF();
        value.endpoint = input.readUTF();
        value.apiKey = input.readUTF();
        value.maximumResults = input.readInt();
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
