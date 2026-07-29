package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.SearchConfig;

import java.io.IOException;

public final class SearchConfigCodecSelfTest {
    public static void main(String[] args) throws Exception {
        SearchConfig source = new SearchConfig();
        source.enabled = true;
        source.presetId = SearchConfig.BRAVE;
        source.endpoint = "https://api.search.brave.com/res/v1/web/search?q={query}";
        source.apiKey = "secret";
        source.maximumResults = 7;
        byte[] encoded = SearchConfigCodec.encode(source);
        SearchConfig restored = SearchConfigCodec.decode(encoded);
        require(restored.enabled, "enabled");
        require(SearchConfig.BRAVE.equals(restored.presetId), "preset");
        require("secret".equals(restored.apiKey), "key");
        require(restored.maximumResults == 7, "result limit");
        encoded[8] ^= 1;
        boolean rejected = false;
        try { SearchConfigCodec.decode(encoded); }
        catch (IOException expected) { rejected = true; }
        require(rejected, "corruption detected");
        System.out.println("SearchConfigCodecSelfTest passed");
    }

    private static void require(boolean value, String label) {
        if (!value) throw new RuntimeException("failed: " + label);
    }
}
