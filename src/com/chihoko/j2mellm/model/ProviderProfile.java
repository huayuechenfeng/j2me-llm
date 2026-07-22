package com.chihoko.j2mellm.model;

import java.util.Vector;

/** A complete, independently persisted provider slot. */
public final class ProviderProfile {
    public static final int MAX_NAME_CHARS = 64;
    public static final int MAX_ENDPOINT_CHARS = 512;
    public static final int MAX_API_KEY_CHARS = 2048;
    public static final int MAX_MODEL_CHARS = 128;
    public static final int MAX_SYSTEM_PROMPT_CHARS = 4096;
    public static final int MAX_EFFORT_CHARS = 32;
    public static final int THINKING_AUTO = 0;
    public static final int THINKING_ON = 1;
    public static final int THINKING_OFF = 2;

    public static final int THINKING_PROTOCOL_NONE = 0;
    public static final int THINKING_PROTOCOL_OPENAI_EFFORT = 1;
    public static final int THINKING_PROTOCOL_ENABLED_OBJECT = 2;
    public static final int THINKING_PROTOCOL_KIMI = 3;

    public String id;
    public String presetId;
    public String name;
    public String endpoint;
    public String modelsEndpoint;
    public String apiKey;
    public String model;
    public String systemPrompt;
    public boolean stream;
    public int historyMessages;
    public int thinkingMode;
    public String reasoningEffort;
    public int thinkingProtocol;
    public boolean reasoningExpanded;
    public boolean multimodal;
    public boolean endpointOverride;
    public long modelsCachedAt;
    public final Vector cachedModels;

    public ProviderProfile(String profileId, String providerPresetId) {
        id = safe(profileId);
        presetId = safe(providerPresetId);
        name = "";
        endpoint = "";
        modelsEndpoint = "";
        apiKey = "";
        model = "";
        systemPrompt = "You are a helpful assistant.";
        stream = true;
        historyMessages = 12;
        thinkingMode = THINKING_AUTO;
        reasoningEffort = "low";
        thinkingProtocol = THINKING_PROTOCOL_NONE;
        reasoningExpanded = false;
        multimodal = false;
        endpointOverride = false;
        modelsCachedAt = 0L;
        cachedModels = new Vector();
    }

    public boolean isReady() {
        return endpoint != null && endpoint.trim().length() > 0
                && model != null && model.trim().length() > 0;
    }

    public String displayName() {
        if (name != null && name.trim().length() > 0) return name.trim();
        if (model != null && model.trim().length() > 0) return model.trim();
        return "J2ME LLM";
    }

    public boolean isBuiltIn() {
        return !ProviderPresets.CUSTOM.equals(presetId);
    }

    public boolean isEndpointLocked() {
        return isBuiltIn() && !endpointOverride;
    }

    public void clearModelCache() {
        cachedModels.removeAllElements();
        modelsCachedAt = 0L;
    }

    public void addCachedModel(String modelId) {
        if (modelId == null) return;
        modelId = modelId.trim();
        if (modelId.length() == 0 || modelId.length() > MAX_MODEL_CHARS
                || containsModel(modelId)) return;
        if (cachedModels.size() >= ProviderPresets.MAX_CACHED_MODELS) return;
        cachedModels.addElement(modelId);
    }

    public boolean containsModel(String modelId) {
        int i;
        for (i = 0; i < cachedModels.size(); i++) {
            if (modelId.equals(cachedModels.elementAt(i))) return true;
        }
        return false;
    }

    public ProviderProfile copy() {
        ProviderProfile copy = new ProviderProfile(id, presetId);
        copy.name = safe(name);
        copy.endpoint = safe(endpoint);
        copy.modelsEndpoint = safe(modelsEndpoint);
        copy.apiKey = safe(apiKey);
        copy.model = safe(model);
        copy.systemPrompt = safe(systemPrompt);
        copy.stream = stream;
        copy.historyMessages = historyMessages;
        copy.thinkingMode = thinkingMode;
        copy.reasoningEffort = safe(reasoningEffort);
        copy.thinkingProtocol = thinkingProtocol;
        copy.reasoningExpanded = reasoningExpanded;
        copy.multimodal = multimodal;
        copy.endpointOverride = endpointOverride;
        copy.modelsCachedAt = modelsCachedAt;
        int i;
        for (i = 0; i < cachedModels.size(); i++) {
            copy.addCachedModel((String) cachedModels.elementAt(i));
        }
        return copy;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}


