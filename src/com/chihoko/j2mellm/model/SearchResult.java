package com.chihoko.j2mellm.model;

/** A normalized, bounded result returned by any search provider. */
public final class SearchResult {
    public String title;
    public String url;
    public String snippet;
    public String publishedAt;

    public SearchResult(String resultTitle, String resultUrl, String resultSnippet) {
        title = safe(resultTitle);
        url = safe(resultUrl);
        snippet = safe(resultSnippet);
        publishedAt = "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
