
package com.chihoko.j2mellm.provision;

import com.chihoko.j2mellm.model.ProfileState;
import com.chihoko.j2mellm.model.ProviderPresets;
import com.chihoko.j2mellm.model.ProviderProfile;

public final class ProvisioningMapperSelfTest {
    public static void main(String[] args) throws Exception {
        ProfileState before = ProviderPresets.createDefaultState();
        before.activeProfileId = ProviderPresets.KIMI;
        before.find(ProviderPresets.OPENAI).apiKey = "openai-secret";
        before.find(ProviderPresets.CUSTOM).reasoningExpanded = true;
        ProviderProfile custom = before.find(ProviderPresets.CUSTOM);
        custom.endpoint = "http://192.168.1.2:8787/v1/chat/completions";
        custom.modelsEndpoint = "http://192.168.1.2:8787/v1/models";
        custom.model = "local-model";
        custom.thinkingProtocol = ProviderProfile.THINKING_PROTOCOL_ENABLED_OBJECT;

        ProvisioningPackage transport = ProvisioningCodec.decode(
                ProvisioningCodec.encode(ProvisioningMapper.exportProfiles(before)));
        ProfileState after = ProvisioningMapper.importProfiles(transport, before);
        require(ProviderPresets.KIMI.equals(after.activeProfileId), "active profile");
        require("openai-secret".equals(after.find(ProviderPresets.OPENAI).apiKey), "key round trip");
        ProviderProfile restored = after.find(ProviderPresets.CUSTOM);
        require(custom.endpoint.equals(restored.endpoint), "custom gateway endpoint");
        require(restored.thinkingProtocol == ProviderProfile.THINKING_PROTOCOL_ENABLED_OBJECT,
                "custom thinking dialect");
        require(restored.reasoningExpanded, "local fold preference preserved");
        require(restored.containsModel("local-model"), "selected model seeds cache");
        partialImportPreservesOmittedProfiles();
        customAlwaysThinkingNormalizesOff();
        System.out.println("ProvisioningMapperSelfTest passed");
    }

    private static void partialImportPreservesOmittedProfiles() throws Exception {
        ProfileState before = ProviderPresets.createDefaultState();
        before.activeProfileId = ProviderPresets.KIMI;
        before.legacyMigrated = true;

        ProviderProfile openai = before.find(ProviderPresets.OPENAI);
        openai.apiKey = "keep-openai-key";
        openai.model = "keep-openai-model";
        openai.historyMessages = 17;
        openai.addCachedModel("cached-openai-model");

        ProviderProfile deepseek = before.find(ProviderPresets.DEEPSEEK);
        deepseek.apiKey = "keep-deepseek-key";
        deepseek.systemPrompt = "keep this prompt";
        deepseek.multimodal = true;

        ProvisioningPackage partial = new ProvisioningPackage();
        ProvisioningProfile incoming = new ProvisioningProfile();
        incoming.id = ProviderPresets.CUSTOM;
        incoming.preset = ProviderPresets.CUSTOM;
        incoming.name = "Imported custom";
        incoming.endpoint = "http://127.0.0.1:8787/v1/chat/completions";
        incoming.modelsEndpoint = "http://127.0.0.1:8787/v1/models";
        incoming.apiKey = "new-custom-key";
        incoming.model = "new-custom-model";
        incoming.historyMessages = 6;
        partial.addProfile(incoming);

        ProfileState after = ProvisioningMapper.importProfiles(partial, before);
        require(ProviderPresets.KIMI.equals(after.activeProfileId),
                "blank package active preserves previous active");
        require(after.legacyMigrated, "legacy migration marker preserved");
        require("keep-openai-key".equals(after.find(ProviderPresets.OPENAI).apiKey),
                "omitted OpenAI key preserved");
        require("keep-openai-model".equals(after.find(ProviderPresets.OPENAI).model),
                "omitted OpenAI settings preserved");
        require(after.find(ProviderPresets.OPENAI).historyMessages == 17,
                "omitted OpenAI history setting preserved");
        require(after.find(ProviderPresets.OPENAI).containsModel("cached-openai-model"),
                "omitted OpenAI model cache preserved");
        require("keep-deepseek-key".equals(after.find(ProviderPresets.DEEPSEEK).apiKey),
                "omitted DeepSeek key preserved");
        require("keep this prompt".equals(after.find(ProviderPresets.DEEPSEEK).systemPrompt),
                "omitted DeepSeek prompt preserved");
        require(after.find(ProviderPresets.DEEPSEEK).multimodal,
                "omitted DeepSeek switch preserved");
        require("new-custom-key".equals(after.find(ProviderPresets.CUSTOM).apiKey),
                "included custom profile replaced");
        require("keep-openai-key".equals(before.find(ProviderPresets.OPENAI).apiKey),
                "import result is detached from previous state");

        after.find(ProviderPresets.OPENAI).apiKey = "changed-after-import";
        require("keep-openai-key".equals(before.find(ProviderPresets.OPENAI).apiKey),
                "copy does not mutate previous state");
    }

    private static void customAlwaysThinkingNormalizesOff() throws Exception {
        ProfileState before = ProviderPresets.createDefaultState();
        ProvisioningPackage partial = new ProvisioningPackage();
        ProvisioningProfile incoming = new ProvisioningProfile();
        incoming.id = ProviderPresets.CUSTOM;
        incoming.preset = ProviderPresets.CUSTOM;
        incoming.endpoint = "http://127.0.0.1:8787/v1/chat/completions";
        incoming.modelsEndpoint = "http://127.0.0.1:8787/v1/models";
        incoming.model = "always-think-model";
        incoming.historyMessages = 8;
        incoming.thinkingProtocol = ProviderProfile.THINKING_PROTOCOL_KIMI;
        incoming.thinkingMode = ProviderProfile.THINKING_OFF;
        partial.addProfile(incoming);

        ProviderProfile custom = ProvisioningMapper.importProfiles(partial, before)
                .find(ProviderPresets.CUSTOM);
        require(custom.thinkingProtocol == ProviderProfile.THINKING_PROTOCOL_KIMI,
                "custom Kimi protocol imported");
        require(custom.thinkingMode == ProviderProfile.THINKING_ON,
                "custom always-thinking off normalized to on");
    }

    private static void require(boolean value, String label) {
        if (!value) throw new RuntimeException("failed: " + label);
    }
}


