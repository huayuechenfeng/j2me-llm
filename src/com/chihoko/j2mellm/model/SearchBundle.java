package com.chihoko.j2mellm.model;

import java.util.Vector;

/** Search context attached to the user message that triggered retrieval. */
public final class SearchBundle {
    public static final int MAX_RESULTS = 10;

    public String query;
    public String provider;
    public long searchedAt;
    public final Vector results = new Vector();

    public SearchBundle(String searchQuery, String providerName) {
        query = searchQuery == null ? "" : searchQuery;
        provider = providerName == null ? "" : providerName;
        searchedAt = System.currentTimeMillis();
    }

    public void add(SearchResult result) {
        if (result != null && results.size() < MAX_RESULTS) results.addElement(result);
    }

    public int getCharacterCost() {
        int cost = safeLength(query) + safeLength(provider) + 32;
        int i;
        for (i = 0; i < results.size(); i++) {
            SearchResult result = (SearchResult) results.elementAt(i);
            cost += safeLength(result.title) + safeLength(result.url)
                    + safeLength(result.snippet) + safeLength(result.publishedAt) + 32;
        }
        return cost;
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }
}
