package com.chihoko.j2mellm.model;

/** Global web-search configuration. */
public final class SearchConfig {
    public static final int MAX_PRESET_CHARS = 32;
    public static final int MAX_ENDPOINT_CHARS = 512;
    public static final int MAX_API_KEY_CHARS = 256;

    public static final String FREE_COMPOSITE = "free-composite";
    public static final String PUBLIC_SEARXNG = "public-searxng";
    public static final String BRAVE = "brave";
    public static final String TAVILY = "tavily";
    public static final String EXA = "exa";
    public static final String CUSTOM = "custom";

    public boolean enabled;
    public String presetId = FREE_COMPOSITE;
    public String endpoint = "";
    public String apiKey = "";
    public int maximumResults = 5;

    public SearchConfig copy() {
        SearchConfig copy = new SearchConfig();
        copy.enabled = enabled;
        copy.presetId = safe(presetId);
        copy.endpoint = safe(endpoint);
        copy.apiKey = safe(apiKey);
        copy.maximumResults = maximumResults;
        return copy;
    }

    public void normalize() {
        if (!isPreset(presetId)) presetId = CUSTOM;
        endpoint = safe(endpoint).trim();
        apiKey = safe(apiKey).trim();
        if (maximumResults < 1) maximumResults = 1;
        if (maximumResults > 10) maximumResults = 10;
    }

    public boolean requiresKey() {
        return BRAVE.equals(presetId) || TAVILY.equals(presetId)
                || EXA.equals(presetId);
    }

    public static boolean isPreset(String value) {
        return FREE_COMPOSITE.equals(value) || PUBLIC_SEARXNG.equals(value)
                || BRAVE.equals(value) || TAVILY.equals(value)
                || EXA.equals(value) || CUSTOM.equals(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
