

package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ProfileState;
import com.chihoko.j2mellm.model.ProviderPresets;
import com.chihoko.j2mellm.model.ProviderProfile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** RMS-independent profile serialization with corruption detection. */
public final class ProfileCodec {
    public static final int FORMAT_VERSION = 2;
    private static final int MAGIC = 0x4A324C50;
    private static final int MAX_RECORD_BYTES = 131072;
    private static final int MAX_PROFILE_COUNT = 8;

    private ProfileCodec() { }

    public static byte[] encode(ProfileState state) throws IOException {
        if (state == null) throw new IOException("Missing profile state");
        ProviderPresets.ensureFixedProfiles(state);

        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(payloadBytes);
        output.writeInt(MAGIC);
        output.writeInt(FORMAT_VERSION);
        output.writeUTF(limited(state.activeProfileId, 32, "active profile id"));
        output.writeBoolean(state.legacyMigrated);
        output.writeInt(state.profiles.size());
        int i;
        for (i = 0; i < state.profiles.size(); i++) {
            writeProfile(output, (ProviderProfile) state.profiles.elementAt(i));
        }
        output.flush();
        byte[] payload = payloadBytes.toByteArray();
        output.close();

        ByteArrayOutputStream recordBytes = new ByteArrayOutputStream(payload.length + 4);
        recordBytes.write(payload);
        DataOutputStream record = new DataOutputStream(recordBytes);
        record.writeInt(crc32(payload, 0, payload.length));
        record.flush();
        byte[] encoded = recordBytes.toByteArray();
        record.close();
        if (encoded.length > MAX_RECORD_BYTES) {
            throw new IOException("Profile record exceeds storage limit");
        }
        return encoded;
    }

    public static ProfileState decode(byte[] record) throws IOException {
        if (record == null || record.length < 16 || record.length > MAX_RECORD_BYTES) {
            throw new IOException("Invalid profile record size");
        }
        int payloadLength = record.length - 4;
        int expected = readInt(record, payloadLength);
        int actual = crc32(record, 0, payloadLength);
        if (expected != actual) throw new IOException("Profile checksum mismatch");

        DataInputStream input = new DataInputStream(new ByteArrayInputStream(record, 0, payloadLength));
        if (input.readInt() != MAGIC) throw new IOException("Invalid profile magic");
        int version = input.readInt();
        if (version != FORMAT_VERSION) throw new IOException("Unsupported profile version: " + version);
        ProfileState state = new ProfileState();
        state.activeProfileId = readLimited(input, 32, "active profile id");
        state.legacyMigrated = input.readBoolean();
        int count = input.readInt();
        if (count < 0 || count > MAX_PROFILE_COUNT) throw new IOException("Invalid profile count");
        int i;
        for (i = 0; i < count; i++) state.profiles.addElement(readProfile(input));
        ProviderPresets.ensureFixedProfiles(state);
        return state;
    }

    static int crc32(byte[] data, int offset, int length) {
        int crc = 0xffffffff;
        int i;
        for (i = offset; i < offset + length; i++) {
            crc ^= data[i] & 0xff;
            int bit;
            for (bit = 0; bit < 8; bit++) {
                if ((crc & 1) != 0) crc = (crc >>> 1) ^ 0xedb88320;
                else crc >>>= 1;
            }
        }
        return ~crc;
    }

    private static void writeProfile(DataOutputStream output, ProviderProfile profile) throws IOException {
        output.writeUTF(limited(profile.id, 32, "profile id"));
        output.writeUTF(limited(profile.presetId, 32, "preset id"));
        output.writeUTF(limited(profile.name, ProviderProfile.MAX_NAME_CHARS, "profile name"));
        output.writeUTF(limited(profile.endpoint, ProviderProfile.MAX_ENDPOINT_CHARS, "chat endpoint"));
        output.writeUTF(limited(profile.modelsEndpoint, ProviderProfile.MAX_ENDPOINT_CHARS, "models endpoint"));
        output.writeUTF(limited(profile.apiKey, ProviderProfile.MAX_API_KEY_CHARS, "API key"));
        output.writeUTF(limited(profile.model, ProviderProfile.MAX_MODEL_CHARS, "model"));
        output.writeUTF(limited(profile.systemPrompt, ProviderProfile.MAX_SYSTEM_PROMPT_CHARS, "system prompt"));
        output.writeBoolean(profile.stream);
        output.writeInt(profile.historyMessages);
        output.writeInt(profile.thinkingMode);
        output.writeUTF(limited(profile.reasoningEffort, ProviderProfile.MAX_EFFORT_CHARS, "reasoning effort"));
        output.writeInt(profile.thinkingProtocol);
        output.writeBoolean(profile.reasoningExpanded);
        output.writeBoolean(profile.multimodal);
        output.writeBoolean(profile.endpointOverride);
        output.writeLong(profile.modelsCachedAt);
        int count = profile.cachedModels.size();
        if (count > ProviderPresets.MAX_CACHED_MODELS) count = ProviderPresets.MAX_CACHED_MODELS;
        output.writeInt(count);
        int i;
        for (i = 0; i < count; i++) {
            output.writeUTF(limited((String) profile.cachedModels.elementAt(i),
                    ProviderProfile.MAX_MODEL_CHARS, "cached model"));
        }
    }

    private static ProviderProfile readProfile(DataInputStream input) throws IOException {
        String id = readLimited(input, 32, "profile id");
        ProviderProfile profile = new ProviderProfile(id,
                readLimited(input, 32, "preset id"));
        profile.name = readLimited(input, ProviderProfile.MAX_NAME_CHARS, "profile name");
        profile.endpoint = readLimited(input, ProviderProfile.MAX_ENDPOINT_CHARS, "chat endpoint");
        profile.modelsEndpoint = readLimited(input, ProviderProfile.MAX_ENDPOINT_CHARS, "models endpoint");
        profile.apiKey = readLimited(input, ProviderProfile.MAX_API_KEY_CHARS, "API key");
        profile.model = readLimited(input, ProviderProfile.MAX_MODEL_CHARS, "model");
        profile.systemPrompt = readLimited(input, ProviderProfile.MAX_SYSTEM_PROMPT_CHARS, "system prompt");
        profile.stream = input.readBoolean();
        profile.historyMessages = input.readInt();
        profile.thinkingMode = input.readInt();
        profile.reasoningEffort = readLimited(input, ProviderProfile.MAX_EFFORT_CHARS,
                "reasoning effort");
        profile.thinkingProtocol = input.readInt();
        profile.reasoningExpanded = input.readBoolean();
        profile.multimodal = input.readBoolean();
        profile.endpointOverride = input.readBoolean();
        profile.modelsCachedAt = input.readLong();
        int count = input.readInt();
        if (count < 0 || count > ProviderPresets.MAX_CACHED_MODELS) {
            throw new IOException("Invalid cached model count");
        }
        int i;
        for (i = 0; i < count; i++) {
            String model = readLimited(input, ProviderProfile.MAX_MODEL_CHARS, "cached model");
            profile.addCachedModel(model);
        }
        return profile;
    }

    static boolean isValidRecord(byte[] record) {
        if (record == null || record.length < 16 || record.length > MAX_RECORD_BYTES) return false;
        int payloadLength = record.length - 4;
        return readInt(record, 0) == MAGIC
                && readInt(record, 4) == FORMAT_VERSION
                && readInt(record, payloadLength) == crc32(record, 0, payloadLength);
    }

    private static String limited(String value, int maximum, String field) throws IOException {
        if (value == null) return "";
        if (value.length() > maximum) throw new IOException(field + " is too long");
        return value;
    }

    private static String readLimited(DataInputStream input, int maximum, String field)
            throws IOException {
        String value = input.readUTF();
        if (value.length() > maximum) throw new IOException(field + " is too long");
        return value;
    }

    private static int readInt(byte[] value, int offset) {
        return ((value[offset] & 0xff) << 24)
                | ((value[offset + 1] & 0xff) << 16)
                | ((value[offset + 2] & 0xff) << 8)
                | (value[offset + 3] & 0xff);
    }
}


