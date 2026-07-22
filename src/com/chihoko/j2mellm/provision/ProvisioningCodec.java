
package com.chihoko.j2mellm.provision;

import com.chihoko.j2mellm.model.ProviderProfile;
import com.chihoko.j2mellm.util.Base64;
import com.chihoko.j2mellm.util.Crc32;
import com.chihoko.j2mellm.util.Json;
import com.chihoko.j2mellm.util.Utf8;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

/** Versioned, checksummed codec for the offline .j2cfg configuration package. */
public final class ProvisioningCodec {
    public static final String FORMAT = "j2me-llm-config";
    public static final int VERSION = 2;
    public static final int MAX_FILE_BYTES = 32768;
    public static final int MAX_PAYLOAD_BYTES = 24576;
    public static final int MAX_PROFILES = 8;

    private static final int MAX_ID_CHARS = 32;

    private ProvisioningCodec() {
    }

    public static byte[] encode(ProvisioningPackage config) throws IOException {
        if (config == null) throw new IOException("配置包为空");
        validatePackage(config);
        byte[] payload = Utf8.encode(buildPayload(config));
        if (payload.length > MAX_PAYLOAD_BYTES) throw new IOException("配置内容超过 24 KB");

        StringBuffer envelope = new StringBuffer(payload.length * 2);
        envelope.append('{');
        field(envelope, "format", FORMAT); envelope.append(',');
        numberField(envelope, "version", VERSION); envelope.append(',');
        field(envelope, "encoding", "base64"); envelope.append(',');
        field(envelope, "payload", Base64.encode(payload)); envelope.append(',');
        field(envelope, "crc32", Crc32.hex(Crc32.compute(payload)));
        envelope.append('}');
        byte[] result = Utf8.encode(envelope.toString());
        if (result.length > MAX_FILE_BYTES) throw new IOException("配置包超过 32 KB");
        return result;
    }

    public static ProvisioningPackage decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) throw new IOException("配置包为空");
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("配置包超过 32 KB");
        Hashtable envelope = requireObject(Json.parse(Utf8.decode(bytes)), "配置包外层必须是对象");
        requireEquals(text(envelope, "format", true), FORMAT, "配置包格式不受支持");
        if (integer(envelope, "version", -1) != VERSION) throw new IOException("配置包版本不受支持");
        requireEquals(text(envelope, "encoding", true), "base64", "配置包编码不受支持");

        String encodedPayload = text(envelope, "payload", true);
        if (encodedPayload.length() > ((MAX_PAYLOAD_BYTES + 2) / 3) * 4) {
            throw new IOException("配置内容超过 24 KB");
        }
        byte[] payload = Base64.decode(encodedPayload);
        if (payload.length > MAX_PAYLOAD_BYTES) throw new IOException("配置内容超过 24 KB");
        String expectedCrc = text(envelope, "crc32", true);
        if (!Crc32.hex(Crc32.compute(payload)).equalsIgnoreCase(expectedCrc)) {
            throw new IOException("配置包校验失败，文件可能已损坏或被修改");
        }

        Hashtable root = requireObject(Json.parse(Utf8.decode(payload)), "配置内容必须是对象");
        ProvisioningPackage result = new ProvisioningPackage();
        result.setActiveProfileId(text(root, "activeProfile", false));
        Vector profiles = Json.array(root.get("profiles"));
        if (profiles == null) throw new IOException("配置内容缺少 profiles");
        if (profiles.size() == 0) throw new IOException("配置包至少需要一个档案");
        if (profiles.size() > MAX_PROFILES) throw new IOException("配置包最多允许 8 个档案");
        int i;
        for (i = 0; i < profiles.size(); i++) {
            result.addProfile(readProfile(requireObject(profiles.elementAt(i), "档案必须是对象")));
        }
        validatePackage(result);
        return result;
    }

    private static String buildPayload(ProvisioningPackage config) {
        StringBuffer out = new StringBuffer(1024);
        out.append('{');
        field(out, "activeProfile", config.getActiveProfileId());
        out.append(',').append(Json.quote("profiles")).append(':').append('[');
        Vector profiles = config.getProfiles();
        int i;
        for (i = 0; i < profiles.size(); i++) {
            if (i > 0) out.append(',');
            appendProfile(out, (ProvisioningProfile) profiles.elementAt(i));
        }
        out.append(']').append('}');
        return out.toString();
    }

    private static void appendProfile(StringBuffer out, ProvisioningProfile profile) {
        out.append('{');
        field(out, "id", profile.id); out.append(',');
        field(out, "preset", profile.preset); out.append(',');
        field(out, "name", profile.name); out.append(',');
        field(out, "endpoint", profile.endpoint); out.append(',');
        field(out, "modelsEndpoint", profile.modelsEndpoint); out.append(',');
        field(out, "apiKey", profile.apiKey); out.append(',');
        field(out, "model", profile.model); out.append(',');
        field(out, "systemPrompt", profile.systemPrompt); out.append(',');
        booleanField(out, "stream", profile.stream); out.append(',');
        numberField(out, "historyMessages", profile.historyMessages); out.append(',');
        numberField(out, "thinkingMode", profile.thinkingMode); out.append(',');
        numberField(out, "thinkingProtocol", profile.thinkingProtocol); out.append(',');
        field(out, "reasoningEffort", profile.reasoningEffort); out.append(',');
        booleanField(out, "multimodal", profile.multimodal); out.append(',');
        booleanField(out, "endpointOverridden", profile.endpointOverridden);
        out.append('}');
    }

    private static ProvisioningProfile readProfile(Hashtable source) throws IOException {
        ProvisioningProfile profile = new ProvisioningProfile();
        profile.id = text(source, "id", true);
        profile.preset = text(source, "preset", false);
        if (profile.preset.length() == 0) profile.preset = "custom";
        profile.name = text(source, "name", false);
        profile.endpoint = text(source, "endpoint", false);
        profile.modelsEndpoint = text(source, "modelsEndpoint", false);
        profile.apiKey = text(source, "apiKey", false);
        profile.model = text(source, "model", false);
        profile.systemPrompt = text(source, "systemPrompt", false);
        profile.stream = bool(source, "stream", true);
        profile.historyMessages = integer(source, "historyMessages", 8);
        profile.thinkingMode = integer(source, "thinkingMode", 0);
        profile.thinkingProtocol = integer(source, "thinkingProtocol", 0);
        profile.reasoningEffort = text(source, "reasoningEffort", false);
        profile.multimodal = bool(source, "multimodal", false);
        profile.endpointOverridden = bool(source, "endpointOverridden", false);
        return profile;
    }

    private static void validatePackage(ProvisioningPackage config) throws IOException {
        Vector profiles = config.getProfiles();
        if (profiles.size() == 0) throw new IOException("配置包至少需要一个档案");
        if (profiles.size() > MAX_PROFILES) throw new IOException("配置包最多允许 8 个档案");
        bounded(config.getActiveProfileId(), MAX_ID_CHARS, "活动档案标识");
        Hashtable ids = new Hashtable();
        int i;
        for (i = 0; i < profiles.size(); i++) {
            ProvisioningProfile profile = (ProvisioningProfile) profiles.elementAt(i);
            if (profile == null) throw new IOException("档案为空");
            normalize(profile);
            boundedRequired(profile.id, MAX_ID_CHARS, "档案标识");
            if (ids.get(profile.id) != null) throw new IOException("档案标识重复：" + profile.id);
            ids.put(profile.id, profile.id);
            boundedRequired(profile.preset, MAX_ID_CHARS, "预设标识");
            bounded(profile.name, ProviderProfile.MAX_NAME_CHARS, "档案名称");
            bounded(profile.endpoint, ProviderProfile.MAX_ENDPOINT_CHARS, "聊天端点");
            bounded(profile.modelsEndpoint, ProviderProfile.MAX_ENDPOINT_CHARS, "模型端点");
            bounded(profile.apiKey, ProviderProfile.MAX_API_KEY_CHARS, "API 密钥");
            bounded(profile.model, ProviderProfile.MAX_MODEL_CHARS, "模型名称");
            bounded(profile.systemPrompt, ProviderProfile.MAX_SYSTEM_PROMPT_CHARS, "系统提示词");
            bounded(profile.reasoningEffort, ProviderProfile.MAX_EFFORT_CHARS, "思考强度");
            if (profile.historyMessages < 2 || profile.historyMessages > 24) {
                throw new IOException("历史消息数必须在 2 到 24 之间");
            }
            if (profile.thinkingMode < 0 || profile.thinkingMode > 2) {
                throw new IOException("思考模式值无效");
            }
            if (profile.thinkingProtocol < 0 || profile.thinkingProtocol > 3) {
                throw new IOException("思考协议值无效");
            }
        }
        if (config.getActiveProfileId().length() > 0 && ids.get(config.getActiveProfileId()) == null) {
            throw new IOException("活动档案不存在");
        }
    }

    private static void normalize(ProvisioningProfile profile) {
        if (profile.id == null) profile.id = "";
        if (profile.preset == null) profile.preset = "custom";
        if (profile.name == null) profile.name = "";
        if (profile.endpoint == null) profile.endpoint = "";
        if (profile.modelsEndpoint == null) profile.modelsEndpoint = "";
        if (profile.apiKey == null) profile.apiKey = "";
        if (profile.model == null) profile.model = "";
        if (profile.systemPrompt == null) profile.systemPrompt = "";
        if (profile.reasoningEffort == null) profile.reasoningEffort = "";
    }

    private static void field(StringBuffer out, String name, String value) {
        out.append(Json.quote(name)).append(':').append(Json.quote(value == null ? "" : value));
    }

    private static void numberField(StringBuffer out, String name, int value) {
        out.append(Json.quote(name)).append(':').append(value);
    }

    private static void booleanField(StringBuffer out, String name, boolean value) {
        out.append(Json.quote(name)).append(':').append(value ? "true" : "false");
    }

    private static Hashtable requireObject(Object value, String error) throws IOException {
        Hashtable object = Json.object(value);
        if (object == null) throw new IOException(error);
        return object;
    }

    private static String text(Hashtable source, String key, boolean required) throws IOException {
        String value = Json.string(source.get(key));
        if (value == null) {
            if (required) throw new IOException("缺少字段：" + key);
            return "";
        }
        return value;
    }

    private static int integer(Hashtable source, String key, int fallback) throws IOException {
        Object value = source.get(key);
        if (value == null) return fallback;
        String text = value instanceof String ? (String) value : null;
        if (text == null) throw new IOException("字段不是整数：" + key);
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException failure) {
            throw new IOException("字段不是整数：" + key);
        }
    }

    private static boolean bool(Hashtable source, String key, boolean fallback) throws IOException {
        Object value = source.get(key);
        if (value == null) return fallback;
        if (value == Boolean.TRUE) return true;
        if (value == Boolean.FALSE) return false;
        throw new IOException("字段不是布尔值：" + key);
    }

    private static void requireEquals(String actual, String expected, String error) throws IOException {
        if (!expected.equals(actual)) throw new IOException(error);
    }

    private static void boundedRequired(String value, int limit, String label) throws IOException {
        if (value == null || value.length() == 0) throw new IOException(label + "不能为空");
        bounded(value, limit, label);
    }

    private static void bounded(String value, int limit, String label) throws IOException {
        if (value != null && value.length() > limit) throw new IOException(label + "过长");
    }
}


