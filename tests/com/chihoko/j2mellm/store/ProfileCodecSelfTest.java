

package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ProfileState;
import com.chihoko.j2mellm.model.ProviderPresets;
import com.chihoko.j2mellm.model.ProviderProfile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class ProfileCodecSelfTest {
    public static void main(String[] args) throws Exception {
        testRoundTripAndIndependentSlots();
        testCorruptionAndBackupRecovery();
        testLegacyMigrationDecoder();
        testLimitsRejectRatherThanTruncate();
        testRecordSizeLimit();
        testKnownCrc32();
        System.out.println("ProfileCodecSelfTest passed");
    }

    private static void testRoundTripAndIndependentSlots() throws Exception {
        ProfileState state = ProviderPresets.createDefaultState();
        ProviderProfile openai = state.find(ProviderPresets.OPENAI);
        ProviderProfile deepseek = state.find(ProviderPresets.DEEPSEEK);
        openai.apiKey = "sk-openai";
        openai.model = "gpt-test";
        openai.multimodal = true;
        openai.addCachedModel("gpt-test");
        deepseek.apiKey = "sk-deepseek";
        deepseek.thinkingMode = ProviderProfile.THINKING_OFF;
        state.activeProfileId = ProviderPresets.DEEPSEEK;
        state.legacyMigrated = true;

        ProfileState decoded = ProfileCodec.decode(ProfileCodec.encode(state));
        require(decoded.profiles.size() == 4, "four fixed profiles");
        require(ProviderPresets.DEEPSEEK.equals(decoded.activeProfileId), "active slot");
        require("sk-openai".equals(decoded.find(ProviderPresets.OPENAI).apiKey), "OpenAI key");
        require("sk-deepseek".equals(decoded.find(ProviderPresets.DEEPSEEK).apiKey), "DeepSeek key");
        require(decoded.find(ProviderPresets.OPENAI).multimodal, "multimodal setting");
        require(decoded.find(ProviderPresets.OPENAI).containsModel("gpt-test"), "model cache");
        require(decoded.legacyMigrated, "migration marker");
    }

    private static void testCorruptionAndBackupRecovery() throws Exception {
        byte[] backup = ProfileCodec.encode(ProviderPresets.createDefaultState());
        byte[] primary = new byte[backup.length];
        System.arraycopy(backup, 0, primary, 0, backup.length);
        primary[primary.length / 2] ^= 0x41;
        boolean rejected = false;
        try {
            ProfileCodec.decode(primary);
        } catch (IOException expected) {
            rejected = true;
        }
        require(rejected, "corrupt primary rejected");
        require(ProfileCodec.decode(backup).profiles.size() == 4, "backup remains recoverable");
    }

    private static void testLegacyMigrationDecoder() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(1);
        output.writeUTF("Old provider");
        output.writeUTF("https://gateway.example/v1/chat/completions");
        output.writeUTF("old-key");
        output.writeUTF("old-model");
        output.writeUTF("旧系统提示");
        output.writeBoolean(false);
        output.writeInt(7);
        output.flush();

        ProviderProfile migrated = LegacyConfigCodec.decode(bytes.toByteArray());
        require(ProviderPresets.CUSTOM.equals(migrated.id), "legacy target custom");
        require("自定义（旧配置）".equals(migrated.name), "migration label");
        require("https://gateway.example/v1/models".equals(migrated.modelsEndpoint), "models URL derived");
        require("old-key".equals(migrated.apiKey), "legacy key preserved");
        require("old-model".equals(migrated.model), "legacy model preserved");
        require(!migrated.stream && migrated.historyMessages == 7, "legacy options preserved");
    }

    private static void testLimitsRejectRatherThanTruncate() throws Exception {
        ProfileState state = ProviderPresets.createDefaultState();
        ProviderProfile profile = state.find(ProviderPresets.OPENAI);
        StringBuffer model = new StringBuffer();
        int i;
        for (i = 0; i <= ProviderProfile.MAX_MODEL_CHARS; i++) model.append('x');
        profile.model = model.toString();
        boolean rejected = false;
        try {
            ProfileCodec.encode(state);
        } catch (IOException expected) {
            rejected = true;
        }
        require(rejected, "oversize field rejected");

        byte[] valid = ProfileCodec.encode(ProviderPresets.createDefaultState());
        require(ProfileCodec.isValidRecord(valid), "header and CRC validator accepts record");
        valid[valid.length - 1] ^= 1;
        require(!ProfileCodec.isValidRecord(valid), "header and CRC validator rejects corruption");
    }

    private static void testRecordSizeLimit() throws Exception {
        ProfileState state = ProviderPresets.createDefaultState();
        StringBuffer prompt = new StringBuffer();
        int i;
        for (i = 0; i < ProviderProfile.MAX_SYSTEM_PROMPT_CHARS; i++) prompt.append('中');
        int p;
        for (p = 0; p < state.profiles.size(); p++) {
            ProviderProfile profile = (ProviderProfile) state.profiles.elementAt(p);
            profile.systemPrompt = prompt.toString();
            int modelIndex;
            for (modelIndex = 0; modelIndex < ProviderPresets.MAX_CACHED_MODELS; modelIndex++) {
                StringBuffer model = new StringBuffer();
                model.append(modelIndex).append('-');
                while (model.length() < ProviderProfile.MAX_MODEL_CHARS) model.append('型');
                profile.addCachedModel(model.toString());
            }
        }
        boolean rejected = false;
        try {
            ProfileCodec.encode(state);
        } catch (IOException expected) {
            rejected = true;
        }
        require(rejected, "oversize record rejected before RMS write");
    }

    private static void testKnownCrc32() throws Exception {
        byte[] input = "123456789".getBytes("US-ASCII");
        require(ProfileCodec.crc32(input, 0, input.length) == 0xcbf43926, "CRC-32 vector");
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new RuntimeException("failed: " + name);
    }
}


