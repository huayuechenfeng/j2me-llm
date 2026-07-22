



package com.chihoko.j2mellm.model;

import java.util.Vector;

/** Stable preset identifiers and their resettable defaults. */
public final class ProviderPresets {
    public static final String OPENAI = "openai";
    public static final String DEEPSEEK = "deepseek";
    public static final String KIMI = "kimi";
    public static final String CUSTOM = "custom";
    public static final int MAX_CACHED_MODELS = 64;

    private ProviderPresets() { }

    public static Vector createDefaults() {
        Vector profiles = new Vector();
        profiles.addElement(create(OPENAI));
        profiles.addElement(create(DEEPSEEK));
        profiles.addElement(create(KIMI));
        profiles.addElement(create(CUSTOM));
        return profiles;
    }

    public static ProfileState createDefaultState() {
        ProfileState state = new ProfileState();
        Vector defaults = createDefaults();
        int i;
        for (i = 0; i < defaults.size(); i++) state.profiles.addElement(defaults.elementAt(i));
        return state;
    }

    public static ProviderProfile create(String presetId) {
        if (DEEPSEEK.equals(presetId)) return createDeepSeek();
        if (KIMI.equals(presetId)) return createKimi();
        if (CUSTOM.equals(presetId)) return createCustom();
        return createOpenAi();
    }

    public static void ensureFixedProfiles(ProfileState state) {
        String[] ids = {OPENAI, DEEPSEEK, KIMI, CUSTOM};
        Vector ordered = new Vector();
        int i;
        for (i = 0; i < ids.length; i++) {
            ProviderProfile profile = state.find(ids[i]);
            if (profile == null) profile = create(ids[i]);
            normalize(profile, ids[i]);
            ordered.addElement(profile);
        }
        state.profiles.removeAllElements();
        for (i = 0; i < ordered.size(); i++) state.profiles.addElement(ordered.elementAt(i));
        if (state.find(state.activeProfileId) == null) state.activeProfileId = OPENAI;
    }

    public static String deriveModelsEndpoint(String chatEndpoint) {
        if (chatEndpoint == null) return "";
        String endpoint = chatEndpoint.trim();
        int query = endpoint.indexOf('?');
        if (query >= 0) endpoint = endpoint.substring(0, query);
        while (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        String suffix = "/chat/completions";
        if (endpoint.endsWith(suffix)) {
            return endpoint.substring(0, endpoint.length() - suffix.length()) + "/models";
        }
        int v1 = lastIndexOf(endpoint, "/v1");
        if (v1 >= 0) return endpoint.substring(0, v1 + 3) + "/models";
        return endpoint.length() == 0 ? "" : endpoint + "/models";
    }

    public static boolean isKimiAlwaysThinking(ProviderProfile profile) {
        if (profile == null) return false;
        if (CUSTOM.equals(profile.presetId)
                && profile.thinkingProtocol == ProviderProfile.THINKING_PROTOCOL_KIMI) return true;
        if (!KIMI.equals(profile.presetId)) return false;
        String model = profile.model == null ? "" : profile.model.toLowerCase();
        return model.indexOf("kimi-k3") >= 0
                || model.indexOf("kimi-k2.7-code") >= 0;
    }

    /** K3 accepts reasoning_effort; K2.7 Code is always-thinking but accepts no switch field. */
    public static boolean usesKimiReasoningEffort(ProviderProfile profile) {
        if (profile == null) return false;
        String model = profile.model == null ? "" : profile.model.toLowerCase();
        if (model.indexOf("kimi-k2.7-code") >= 0) return false;
        if (model.indexOf("kimi-k3") >= 0) return true;
        return CUSTOM.equals(profile.presetId)
                && profile.thinkingProtocol == ProviderProfile.THINKING_PROTOCOL_KIMI;
    }

    /** Kimi K2.5/K2.6 use thinking.type but do not accept reasoning_effort. */
    public static boolean isKimiThinkingObjectModel(ProviderProfile profile) {
        if (profile == null) return false;
        String model = profile.model == null ? "" : profile.model.toLowerCase();
        return model.indexOf("kimi-k2.5") >= 0
                || model.indexOf("kimi-k2.6") >= 0;
    }

    private static ProviderProfile createOpenAi() {
        ProviderProfile profile = common(OPENAI, "OpenAI");
        profile.endpoint = "https://api.openai.com/v1/chat/completions";
        profile.modelsEndpoint = "https://api.openai.com/v1/models";
        profile.thinkingProtocol = ProviderProfile.THINKING_PROTOCOL_OPENAI_EFFORT;
        return profile;
    }

    private static ProviderProfile createDeepSeek() {
        ProviderProfile profile = common(DEEPSEEK, "DeepSeek");
        profile.endpoint = "https://api.deepseek.com/chat/completions";
        profile.modelsEndpoint = "https://api.deepseek.com/models";
        profile.model = "deepseek-v4-flash";
        profile.thinkingProtocol = ProviderProfile.THINKING_PROTOCOL_ENABLED_OBJECT;
        profile.reasoningEffort = "high";
        return profile;
    }

    private static ProviderProfile createKimi() {
        ProviderProfile profile = common(KIMI, "Kimi");
        profile.endpoint = "https://api.moonshot.cn/v1/chat/completions";
        profile.modelsEndpoint = "https://api.moonshot.cn/v1/models";
        profile.model = "kimi-k3";
        profile.thinkingProtocol = ProviderProfile.THINKING_PROTOCOL_KIMI;
        profile.reasoningEffort = "low";
        return profile;
    }

    private static ProviderProfile createCustom() {
        ProviderProfile profile = common(CUSTOM, "自定义");
        profile.endpoint = "https://api.openai.com/v1/chat/completions";
        profile.modelsEndpoint = "https://api.openai.com/v1/models";
        profile.endpointOverride = true;
        return profile;
    }

    private static ProviderProfile common(String id, String name) {
        ProviderProfile profile = new ProviderProfile(id, id);
        profile.name = name;
        return profile;
    }

    private static int lastIndexOf(String value, String part) {
        int i;
        for (i = value.length() - part.length(); i >= 0; i--) {
            if (value.substring(i, i + part.length()).equals(part)) return i;
        }
        return -1;
    }

    private static void normalize(ProviderProfile profile, String expectedId) {
        profile.id = expectedId;
        if (profile.presetId == null || profile.presetId.length() == 0) profile.presetId = expectedId;
        if (profile.name == null) profile.name = "";
        if (profile.endpoint == null) profile.endpoint = "";
        if (profile.modelsEndpoint == null || profile.modelsEndpoint.length() == 0) {
            profile.modelsEndpoint = deriveModelsEndpoint(profile.endpoint);
        }
        if (profile.apiKey == null) profile.apiKey = "";
        if (profile.model == null) profile.model = "";
        if (profile.systemPrompt == null) profile.systemPrompt = "";
        if (profile.reasoningEffort == null) profile.reasoningEffort = "";
        if (profile.historyMessages < 2) profile.historyMessages = 2;
        if (profile.historyMessages > 24) profile.historyMessages = 24;
        if (profile.thinkingMode < ProviderProfile.THINKING_AUTO
                || profile.thinkingMode > ProviderProfile.THINKING_OFF) {
            profile.thinkingMode = ProviderProfile.THINKING_AUTO;
        }
        while (profile.cachedModels.size() > MAX_CACHED_MODELS) {
            profile.cachedModels.removeElementAt(profile.cachedModels.size() - 1);
        }
    }
}




