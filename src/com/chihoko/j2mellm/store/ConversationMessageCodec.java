package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ChatMessage;
import com.chihoko.j2mellm.model.SearchBundle;
import com.chihoko.j2mellm.model.SearchResult;
import com.chihoko.j2mellm.util.Crc32;
import com.chihoko.j2mellm.util.Utf8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Long-text-safe codec for one v0.4 conversation message record. */
public final class ConversationMessageCodec {
    private static final int MAGIC = 0x4A324D34;
    private static final int VERSION = 1;
    private static final int MAX_RECORD_BYTES = 2097152;
    private static final int MAX_CONTENT_BYTES = 1048576;
    private static final int MAX_REASONING_BYTES = 524288;
    private static final int MAX_SEARCH_TEXT_BYTES = 131072;

    private ConversationMessageCodec() {
    }

    public static byte[] encode(ChatMessage message) throws IOException {
        if (message == null) throw new IOException("Missing message");
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(payloadBytes);
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        writeText(output, message.role, 32, "role");
        writeText(output, message.getContent(), MAX_CONTENT_BYTES, "content");
        writeText(output, message.getReasoning(), MAX_REASONING_BYTES, "reasoning");
        output.writeBoolean(message.error);
        writeText(output, message.getImageName(), 512, "image name");
        writeText(output, message.getImageMime(), 128, "image mime");
        String source = message.getImageSource();
        if (source != null && source.startsWith("data:")) source = "";
        writeText(output, source, 8192, "image source");
        writeSearch(output, message.getSearchBundle());
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
        if (encoded.length > MAX_RECORD_BYTES) throw new IOException("Message record is too large");
        return encoded;
    }

    public static ChatMessage decode(byte[] record) throws IOException {
        if (record == null || record.length < 20 || record.length > MAX_RECORD_BYTES) {
            throw new IOException("Invalid message record size");
        }
        int payloadLength = record.length - 4;
        if (readInt(record, payloadLength) != Crc32.compute(record, 0, payloadLength)) {
            throw new IOException("Message checksum mismatch");
        }
        DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(record, 0, payloadLength));
        if (input.readInt() != MAGIC || input.readInt() != VERSION) {
            throw new IOException("Unsupported message record");
        }
        String role = readText(input, 32, "role");
        if (!ChatMessage.ROLE_USER.equals(role) && !ChatMessage.ROLE_ASSISTANT.equals(role)) {
            throw new IOException("Invalid message role");
        }
        ChatMessage message = new ChatMessage(role,
                readText(input, MAX_CONTENT_BYTES, "content"));
        message.appendReasoning(readText(input, MAX_REASONING_BYTES, "reasoning"));
        message.error = input.readBoolean();
        message.setImageMetadata(readText(input, 512, "image name"),
                readText(input, 128, "image mime"));
        message.setImageSource(readText(input, 8192, "image source"));
        SearchBundle search = readSearch(input);
        if (search != null) message.setSearchBundle(search);
        return message;
    }

    public static boolean isValid(byte[] record) {
        if (record == null || record.length < 20 || record.length > MAX_RECORD_BYTES) return false;
        int payloadLength = record.length - 4;
        return readInt(record, 0) == MAGIC && readInt(record, 4) == VERSION
                && readInt(record, payloadLength) == Crc32.compute(record, 0, payloadLength);
    }

    private static void writeSearch(DataOutputStream output, SearchBundle search)
            throws IOException {
        output.writeBoolean(search != null);
        if (search == null) return;
        writeText(output, search.query, 4096, "search query");
        writeText(output, search.provider, 256, "search provider");
        output.writeLong(search.searchedAt);
        int count = search.results.size();
        if (count > SearchBundle.MAX_RESULTS) count = SearchBundle.MAX_RESULTS;
        output.writeInt(count);
        int i;
        for (i = 0; i < count; i++) {
            SearchResult result = (SearchResult) search.results.elementAt(i);
            writeText(output, result.title, 2048, "search title");
            writeText(output, result.url, 8192, "search url");
            writeText(output, result.snippet, MAX_SEARCH_TEXT_BYTES, "search snippet");
            writeText(output, result.publishedAt, 256, "published date");
        }
    }

    private static SearchBundle readSearch(DataInputStream input) throws IOException {
        if (!input.readBoolean()) return null;
        SearchBundle search = new SearchBundle(readText(input, 4096, "search query"),
                readText(input, 256, "search provider"));
        search.searchedAt = input.readLong();
        int count = input.readInt();
        if (count < 0 || count > SearchBundle.MAX_RESULTS) {
            throw new IOException("Invalid search result count");
        }
        int i;
        for (i = 0; i < count; i++) {
            SearchResult result = new SearchResult(
                    readText(input, 2048, "search title"),
                    readText(input, 8192, "search url"),
                    readText(input, MAX_SEARCH_TEXT_BYTES, "search snippet"));
            result.publishedAt = readText(input, 256, "published date");
            search.add(result);
        }
        return search;
    }

    private static void writeText(DataOutputStream output, String value, int maximumBytes,
            String field) throws IOException {
        byte[] encoded = Utf8.encode(value == null ? "" : value);
        if (encoded.length > maximumBytes) throw new IOException(field + " is too long");
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readText(DataInputStream input, int maximumBytes, String field)
            throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximumBytes || length > input.available()) {
            throw new IOException("Invalid " + field + " length");
        }
        byte[] value = new byte[length];
        input.readFully(value);
        return Utf8.decode(value);
    }

    private static int readInt(byte[] value, int offset) {
        return ((value[offset] & 0xff) << 24)
                | ((value[offset + 1] & 0xff) << 16)
                | ((value[offset + 2] & 0xff) << 8)
                | (value[offset + 3] & 0xff);
    }
}
