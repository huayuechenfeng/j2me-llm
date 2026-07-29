package com.chihoko.j2mellm.model;

/** Endpoint defaults for supported search APIs. All endpoints remain editable. */
public final class SearchPresets {
    private SearchPresets() {
    }

    public static String defaultEndpoint(String presetId) {
        if (SearchConfig.FREE_COMPOSITE.equals(presetId)) {
            return "https://api.duckduckgo.com/?q={query}&format=json&no_html=1&skip_disambig=1";
        }
        if (SearchConfig.PUBLIC_SEARXNG.equals(presetId)) {
            return "https://search.inetol.net/search?q={query}&format=json&categories=general";
        }
        if (SearchConfig.BRAVE.equals(presetId)) {
            return "https://api.search.brave.com/res/v1/web/search?q={query}&count={count}";
        }
        if (SearchConfig.TAVILY.equals(presetId)) {
            return "https://api.tavily.com/search";
        }
        if (SearchConfig.EXA.equals(presetId)) {
            return "https://api.exa.ai/search";
        }
        return "";
    }

    public static String wikipediaEndpoint(String query) {
        String host = containsCjk(query) ? "zh.wikipedia.org" : "en.wikipedia.org";
        return "https://" + host + "/w/api.php?action=opensearch&search={query}"
                + "&limit={count}&namespace=0&format=json";
    }

    private static boolean containsCjk(String value) {
        int i;
        for (i = 0; value != null && i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x3400 && c <= 0x9fff) return true;
        }
        return false;
    }
}
