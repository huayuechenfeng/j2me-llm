
package com.chihoko.j2mellm.provision;

import com.chihoko.j2mellm.util.Crc32;
import com.chihoko.j2mellm.util.Utf8;

import java.io.IOException;

public final class ProvisioningCodecSelfTest {
    private ProvisioningCodecSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        crcKnownVector();
        roundTrip();
        rejectsTampering();
        rejectsUnknownVersion();
        rejectsOversizedFile();
        rejectsMissingActiveProfile();
        acceptsExactFieldLimits();
        rejectsOverlongFields();
        System.out.println("ProvisioningCodecSelfTest OK");
    }

    private static void crcKnownVector() {
        equal("CBF43926", Crc32.hex(Crc32.compute(Utf8.encode("123456789"))), "CRC-32");
    }

    private static void roundTrip() throws Exception {
        ProvisioningPackage source = sample();
        byte[] encoded = ProvisioningCodec.encode(source);
        ProvisioningPackage decoded = ProvisioningCodec.decode(encoded);
        equal("deepseek", decoded.getActiveProfileId(), "active profile");
        equal(2, decoded.getProfiles().size(), "profile count");
        ProvisioningProfile first = (ProvisioningProfile) decoded.getProfiles().elementAt(0);
        equal("OpenAI 主档案", first.name, "UTF-8 name");
        equal("sk-test-秘密", first.apiKey, "secret");
        equal("你是一个简洁的助手。", first.systemPrompt, "system prompt");
        truth(first.stream, "stream");
        truth(!first.multimodal, "multimodal default");
        ProvisioningProfile second = (ProvisioningProfile) decoded.getProfiles().elementAt(1);
        equal(2, second.thinkingMode, "thinking mode");
        equal("high", second.reasoningEffort, "effort");
    }

    private static void rejectsTampering() throws Exception {
        String encoded = Utf8.decode(ProvisioningCodec.encode(sample()));
        int marker = encoded.indexOf("\"payload\":\"");
        int at = marker + "\"payload\":\"".length() + 5;
        char old = encoded.charAt(at);
        char replacement = old == 'A' ? 'B' : 'A';
        String tampered = encoded.substring(0, at) + replacement + encoded.substring(at + 1);
        expectFailure(Utf8.encode(tampered), "校验失败");
    }

    private static void rejectsUnknownVersion() throws Exception {
        String encoded = Utf8.decode(ProvisioningCodec.encode(sample()));
        encoded = replace(encoded, "\"version\":2", "\"version\":99");
        expectFailure(Utf8.encode(encoded), "版本不受支持");
    }

    private static void rejectsOversizedFile() throws Exception {
        expectFailure(new byte[ProvisioningCodec.MAX_FILE_BYTES + 1], "超过 32 KB");
    }

    private static void rejectsMissingActiveProfile() throws Exception {
        ProvisioningPackage source = sample();
        source.setActiveProfileId("not-there");
        try {
            ProvisioningCodec.encode(source);
            fail("missing active profile was accepted");
        } catch (IOException expected) {
            contains(expected.getMessage(), "活动档案不存在", "missing active message");
        }
    }

    private static void acceptsExactFieldLimits() throws Exception {
        ProvisioningPackage config = new ProvisioningPackage();
        ProvisioningProfile profile = new ProvisioningProfile();
        profile.id = repeat('i', 32);
        profile.preset = repeat('p', 32);
        profile.name = repeat('n', 64);
        profile.endpoint = repeat('e', 512);
        profile.modelsEndpoint = repeat('u', 512);
        profile.apiKey = repeat('k', 2048);
        profile.model = repeat('m', 128);
        profile.systemPrompt = repeat('s', 4096);
        profile.reasoningEffort = repeat('r', 32);
        config.setActiveProfileId(profile.id);
        config.addProfile(profile);
        ProvisioningPackage decoded = ProvisioningCodec.decode(ProvisioningCodec.encode(config));
        ProvisioningProfile restored =
                (ProvisioningProfile) decoded.getProfiles().elementAt(0);
        equal(2048, restored.apiKey.length(), "API key exact limit");
        equal(4096, restored.systemPrompt.length(), "prompt exact limit");
    }

    private static void rejectsOverlongFields() throws Exception {
        ProvisioningPackage config = sample();
        ((ProvisioningProfile) config.getProfiles().elementAt(0)).name = repeat('n', 65);
        expectEncodeFailure(config, "档案名称过长");

        config = sample();
        ((ProvisioningProfile) config.getProfiles().elementAt(0)).endpoint = repeat('e', 513);
        expectEncodeFailure(config, "聊天端点过长");

        config = sample();
        ((ProvisioningProfile) config.getProfiles().elementAt(0)).modelsEndpoint =
                repeat('u', 513);
        expectEncodeFailure(config, "模型端点过长");

        config = sample();
        ((ProvisioningProfile) config.getProfiles().elementAt(0)).apiKey = repeat('k', 2049);
        expectEncodeFailure(config, "API 密钥过长");

        config = sample();
        ((ProvisioningProfile) config.getProfiles().elementAt(0)).model = repeat('m', 129);
        expectEncodeFailure(config, "模型名称过长");

        config = sample();
        ((ProvisioningProfile) config.getProfiles().elementAt(0)).systemPrompt =
                repeat('s', 4097);
        expectEncodeFailure(config, "系统提示词过长");

        config = sample();
        ((ProvisioningProfile) config.getProfiles().elementAt(0)).reasoningEffort =
                repeat('r', 33);
        expectEncodeFailure(config, "思考强度过长");

        config = sample();
        ((ProvisioningProfile) config.getProfiles().elementAt(0)).preset = repeat('p', 33);
        expectEncodeFailure(config, "预设标识过长");

        config = sample();
        ((ProvisioningProfile) config.getProfiles().elementAt(0)).id = repeat('i', 33);
        expectEncodeFailure(config, "档案标识过长");

        config = sample();
        ((ProvisioningProfile) config.getProfiles().elementAt(0)).historyMessages = 25;
        expectEncodeFailure(config, "历史消息数必须在 2 到 24 之间");
    }

    private static void expectEncodeFailure(ProvisioningPackage config, String fragment)
            throws Exception {
        try {
            ProvisioningCodec.encode(config);
            fail("overlong field was accepted: " + fragment);
        } catch (IOException expected) {
            contains(expected.getMessage(), fragment, "overlong field message");
        }
    }

    private static String repeat(char value, int count) {
        StringBuffer out = new StringBuffer(count);
        int i;
        for (i = 0; i < count; i++) out.append(value);
        return out.toString();
    }

    private static ProvisioningPackage sample() {
        ProvisioningPackage config = new ProvisioningPackage();
        config.setActiveProfileId("deepseek");
        ProvisioningProfile openai = new ProvisioningProfile();
        openai.id = "openai";
        openai.preset = "openai";
        openai.name = "OpenAI 主档案";
        openai.endpoint = "https://api.openai.com/v1/chat/completions";
        openai.modelsEndpoint = "https://api.openai.com/v1/models";
        openai.apiKey = "sk-test-秘密";
        openai.model = "gpt-test";
        openai.systemPrompt = "你是一个简洁的助手。";
        openai.stream = true;
        openai.historyMessages = 10;
        config.addProfile(openai);

        ProvisioningProfile deepseek = new ProvisioningProfile();
        deepseek.id = "deepseek";
        deepseek.preset = "deepseek";
        deepseek.name = "DeepSeek";
        deepseek.endpoint = "https://api.deepseek.com/chat/completions";
        deepseek.modelsEndpoint = "https://api.deepseek.com/models";
        deepseek.model = "deepseek-v4-flash";
        deepseek.thinkingMode = 2;
        deepseek.thinkingProtocol = 2;
        deepseek.reasoningEffort = "high";
        deepseek.endpointOverridden = false;
        config.addProfile(deepseek);
        return config;
    }

    private static void expectFailure(byte[] bytes, String fragment) throws Exception {
        try {
            ProvisioningCodec.decode(bytes);
            fail("invalid package was accepted");
        } catch (IOException expected) {
            contains(expected.getMessage(), fragment, "failure message");
        }
    }

    private static String replace(String value, String before, String after) {
        int at = value.indexOf(before);
        if (at < 0) fail("test marker missing");
        return value.substring(0, at) + after + value.substring(at + before.length());
    }

    private static void contains(String value, String fragment, String label) {
        if (value == null || value.indexOf(fragment) < 0) {
            fail(label + ": expected fragment " + fragment + ", got " + value);
        }
    }

    private static void equal(String expected, String actual, String label) {
        if (!expected.equals(actual)) fail(label + ": expected " + expected + ", got " + actual);
    }

    private static void equal(int expected, int actual, String label) {
        if (expected != actual) fail(label + ": expected " + expected + ", got " + actual);
    }

    private static void truth(boolean value, String label) {
        if (!value) fail(label + " failed");
    }

    private static void fail(String message) {
        throw new RuntimeException(message);
    }
}


