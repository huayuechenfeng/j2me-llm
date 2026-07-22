package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ProviderPresets;
import com.chihoko.j2mellm.model.ProviderProfile;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/** Read-only decoder for the v0.1 J2MELLM_CFG record. */
public final class LegacyConfigCodec {
    private static final int FORMAT_VERSION = 1;

    private LegacyConfigCodec() { }

    public static ProviderProfile decode(byte[] record) throws IOException {
        if (record == null || record.length < 8 || record.length > 131072) {
            throw new IOException("Invalid legacy record");
        }
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(record));
        if (input.readInt() != FORMAT_VERSION) throw new IOException("Unsupported legacy version");
        input.readUTF(); // The old free-form display name is replaced with a migration label.
        ProviderProfile profile = ProviderPresets.create(ProviderPresets.CUSTOM);
        profile.name = "自定义（旧配置）";
        profile.endpoint = input.readUTF();
        profile.modelsEndpoint = ProviderPresets.deriveModelsEndpoint(profile.endpoint);
        profile.apiKey = input.readUTF();
        profile.model = input.readUTF();
        profile.systemPrompt = input.readUTF();
        profile.stream = input.readBoolean();
        profile.historyMessages = input.readInt();
        profile.endpointOverride = true;
        if (profile.model != null && profile.model.length() > 0) profile.addCachedModel(profile.model);
        return profile;
    }
}
