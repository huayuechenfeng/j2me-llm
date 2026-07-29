package com.chihoko.j2mellm.net;

import com.chihoko.j2mellm.model.SearchBundle;
import com.chihoko.j2mellm.model.SearchConfig;
import com.chihoko.j2mellm.model.SearchPresets;
import com.chihoko.j2mellm.model.SearchResult;
import com.chihoko.j2mellm.util.Json;
import com.chihoko.j2mellm.util.Utf8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;

/** Cancellable adapter for keyless, commercial and custom JSON search APIs. */
public final class SearchClient implements Runnable {
    private static final int MAX_RESPONSE_BYTES = 131072;

    private SearchConfig config;
    private String query;
    private SearchListener listener;
    private volatile boolean cancelled;
    private HttpConnection activeConnection;

    public synchronized boolean isRunning() {
        return listener != null;
    }

    public synchronized void search(SearchConfig value, String searchQuery,
            SearchListener callback) {
        if (callback == null) throw new NullPointerException("callback");
        if (listener != null) {
            callback.onError("A search request is already running");
            return;
        }
        SearchConfig copy = value == null ? new SearchConfig() : value.copy();
        copy.normalize();
        String text = searchQuery == null ? "" : searchQuery.trim();
        if (text.length() == 0) {
            callback.onError("Search query is empty");
            return;
        }
        if (text.length() > 1024) text = text.substring(0, 1024);
        if (copy.requiresKey() && copy.apiKey.length() == 0) {
            callback.onError("Search API key is required");
            return;
        }
        config = copy;
        query = text;
        listener = callback;
        cancelled = false;
        new Thread(this).start();
    }

    public void cancel() {
        cancelled = true;
        HttpConnection connection;
        synchronized (this) { connection = activeConnection; }
        if (connection != null) {
            try { connection.close(); } catch (IOException ignored) { }
        }
    }

    public void run() {
        SearchListener callback = listener;
        try {
            SearchBundle bundle = execute();
            throwIfCancelled();
            if (callback != null) callback.onResults(bundle);
        } catch (Throwable failure) {
            if (!cancelled && callback != null) {
                String message = failure.getMessage();
                callback.onError(message == null ? failure.toString() : message);
            }
        } finally {
            synchronized (this) {
                activeConnection = null;
                config = null;
                query = null;
                listener = null;
            }
        }
    }

    private SearchBundle execute() throws IOException {
        SearchBundle bundle = new SearchBundle(query, config.presetId);
        if (SearchConfig.FREE_COMPOSITE.equals(config.presetId)) {
            String endpoint = effectiveEndpoint();
            parseDuckDuckGo(get(endpoint), bundle);
            if (bundle.results.size() < config.maximumResults) {
                parseWikipedia(get(expand(SearchPresets.wikipediaEndpoint(query))), bundle);
            }
        } else if (SearchConfig.TAVILY.equals(config.presetId)) {
            String body = "{\"api_key\":" + Json.quote(config.apiKey)
                    + ",\"query\":" + Json.quote(query)
                    + ",\"max_results\":" + config.maximumResults
                    + ",\"include_answer\":false}";
            parseGeneric(post(effectiveEndpoint(), body, null, null), bundle);
        } else if (SearchConfig.EXA.equals(config.presetId)) {
            String body = "{\"query\":" + Json.quote(query)
                    + ",\"numResults\":" + config.maximumResults
                    + ",\"contents\":{\"text\":{\"maxCharacters\":1200}}}";
            parseGeneric(post(effectiveEndpoint(), body,
                    "x-api-key", config.apiKey), bundle);
        } else {
            String header = null;
            String key = null;
            if (SearchConfig.BRAVE.equals(config.presetId)) {
                header = "X-Subscription-Token";
                key = config.apiKey;
            } else if (config.apiKey.length() > 0) {
                header = "Authorization";
                key = "Bearer " + config.apiKey;
            }
            parseGeneric(get(effectiveEndpoint(), header, key), bundle);
        }
        return bundle;
    }

    private String effectiveEndpoint() throws IOException {
        String endpoint = config.endpoint.length() == 0
                ? SearchPresets.defaultEndpoint(config.presetId) : config.endpoint;
        if (endpoint.length() == 0) throw new IOException("Search endpoint is empty");
        return expand(endpoint);
    }

    private String expand(String endpoint) {
        String value = replace(endpoint, "{query}", encodeComponent(query));
        return replace(value, "{count}", Integer.toString(config.maximumResults));
    }

    private String get(String endpoint) throws IOException {
        return get(endpoint, null, null);
    }

    private String get(String endpoint, String header, String headerValue)
            throws IOException {
        return request(endpoint, HttpConnection.GET, null, header, headerValue);
    }

    private String post(String endpoint, String body, String header, String headerValue)
            throws IOException {
        return request(endpoint, HttpConnection.POST, body, header, headerValue);
    }

    private String request(String endpoint, String method, String body,
            String header, String headerValue) throws IOException {
        HttpConnection connection = null;
        InputStream input = null;
        OutputStream output = null;
        try {
            throwIfCancelled();
            connection = (HttpConnection) Connector.open(endpoint, Connector.READ_WRITE, true);
            synchronized (this) { activeConnection = connection; }
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Connection", "close");
            if (header != null && headerValue != null && headerValue.length() > 0) {
                connection.setRequestProperty(header, headerValue);
            }
            if (body != null) {
                byte[] bytes = Utf8.encode(body);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
                output = connection.openOutputStream();
                output.write(bytes);
                output.flush();
            }
            int status = connection.getResponseCode();
            input = connection.openInputStream();
            String response = readAll(input);
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + ": " + errorMessage(response));
            }
            return response;
        } finally {
            synchronized (this) {
                if (activeConnection == connection) activeConnection = null;
            }
            if (output != null) try { output.close(); } catch (IOException ignored) { }
            if (input != null) try { input.close(); } catch (IOException ignored) { }
            if (connection != null) try { connection.close(); } catch (IOException ignored) { }
        }
    }

    private String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
        byte[] buffer = new byte[512];
        while (true) {
            throwIfCancelled();
            int count = input.read(buffer);
            if (count < 0) break;
            if (count == 0) continue;
            if (output.size() + count > MAX_RESPONSE_BYTES) {
                throw new IOException("Search response is too large");
            }
            output.write(buffer, 0, count);
        }
        return Utf8.decode(output.toByteArray());
    }

    private void parseDuckDuckGo(String body, SearchBundle bundle) throws IOException {
        Hashtable root = Json.object(Json.parse(body));
        if (root == null) throw new IOException("Invalid DuckDuckGo response");
        String abstractText = string(root, "AbstractText");
        String abstractUrl = string(root, "AbstractURL");
        String heading = string(root, "Heading");
        add(bundle, heading, abstractUrl, abstractText);
        addDuckTopics(Json.array(root.get("RelatedTopics")), bundle);
    }

    private void addDuckTopics(Vector topics, SearchBundle bundle) {
        if (topics == null) return;
        int i;
        for (i = 0; i < topics.size() && bundle.results.size() < config.maximumResults; i++) {
            Hashtable topic = Json.object(topics.elementAt(i));
            if (topic == null) continue;
            Vector nested = Json.array(topic.get("Topics"));
            if (nested != null) addDuckTopics(nested, bundle);
            else add(bundle, string(topic, "Text"),
                    string(topic, "FirstURL"), string(topic, "Text"));
        }
    }

    private void parseWikipedia(String body, SearchBundle bundle) throws IOException {
        Vector root = Json.array(Json.parse(body));
        if (root == null || root.size() < 4) throw new IOException("Invalid Wikipedia response");
        Vector titles = Json.array(root.elementAt(1));
        Vector snippets = Json.array(root.elementAt(2));
        Vector urls = Json.array(root.elementAt(3));
        if (titles == null || urls == null) return;
        int i;
        for (i = 0; i < titles.size() && i < urls.size()
                && bundle.results.size() < config.maximumResults; i++) {
            String snippet = snippets != null && i < snippets.size()
                    ? Json.string(snippets.elementAt(i)) : "";
            add(bundle, Json.string(titles.elementAt(i)),
                    Json.string(urls.elementAt(i)), snippet);
        }
    }

    private void parseGeneric(String body, SearchBundle bundle) throws IOException {
        Object parsed = Json.parse(body);
        Hashtable root = Json.object(parsed);
        Vector results = root == null ? Json.array(parsed) : Json.array(root.get("results"));
        if (root != null && results == null) {
            Hashtable web = Json.object(root.get("web"));
            if (web != null) results = Json.array(web.get("results"));
        }
        if (root != null && results == null) results = Json.array(root.get("data"));
        if (results == null) throw new IOException("Search response has no results array");
        int i;
        for (i = 0; i < results.size() && bundle.results.size() < config.maximumResults; i++) {
            Hashtable item = Json.object(results.elementAt(i));
            if (item == null) continue;
            String title = first(item, new String[] {"title", "name"});
            String url = first(item, new String[] {"url", "link", "href"});
            String snippet = first(item,
                    new String[] {"content", "snippet", "description", "text"});
            add(bundle, title, url, snippet);
        }
    }

    private void add(SearchBundle bundle, String title, String url, String snippet) {
        String safeUrl = trim(url, 1024);
        if (safeUrl.length() == 0 || containsUrl(bundle, safeUrl)
                || bundle.results.size() >= config.maximumResults) return;
        String safeTitle = trim(title, 256);
        if (safeTitle.length() == 0) safeTitle = safeUrl;
        bundle.add(new SearchResult(safeTitle, safeUrl, trim(snippet, 1600)));
    }

    private boolean containsUrl(SearchBundle bundle, String url) {
        int i;
        for (i = 0; i < bundle.results.size(); i++) {
            SearchResult value = (SearchResult) bundle.results.elementAt(i);
            if (url.equals(value.url)) return true;
        }
        return false;
    }

    private String first(Hashtable object, String[] fields) {
        int i;
        for (i = 0; i < fields.length; i++) {
            String value = string(object, fields[i]);
            if (value.length() > 0) return value;
        }
        return "";
    }

    private String string(Hashtable object, String field) {
        String value = object == null ? null : Json.string(object.get(field));
        return value == null ? "" : value;
    }

    private String trim(String value, int maximum) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }

    private String errorMessage(String body) {
        try {
            Hashtable root = Json.object(Json.parse(body));
            if (root != null) {
                String message = first(root, new String[] {"message", "error", "detail"});
                if (message.length() > 0) return trim(message, 240);
            }
        } catch (Exception ignored) {
        }
        return trim(body, 240);
    }

    private void throwIfCancelled() throws IOException {
        if (cancelled) throw new IOException("Search cancelled");
    }

    private static String replace(String source, String wanted, String replacement) {
        int at = source.indexOf(wanted);
        if (at < 0) return source;
        StringBuffer result = new StringBuffer(source.length() + replacement.length());
        int start = 0;
        while (at >= 0) {
            result.append(source.substring(start, at)).append(replacement);
            start = at + wanted.length();
            at = source.indexOf(wanted, start);
        }
        result.append(source.substring(start));
        return result.toString();
    }

    private static String encodeComponent(String value) {
        byte[] bytes = Utf8.encode(value == null ? "" : value);
        StringBuffer result = new StringBuffer(bytes.length * 3);
        final String hex = "0123456789ABCDEF";
        int i;
        for (i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xff;
            boolean safe = b >= 'a' && b <= 'z' || b >= 'A' && b <= 'Z'
                    || b >= '0' && b <= '9' || b == '-' || b == '_'
                    || b == '.' || b == '~';
            if (safe) result.append((char) b);
            else {
                result.append('%');
                result.append(hex.charAt((b >>> 4) & 0xf));
                result.append(hex.charAt(b & 0xf));
            }
        }
        return result.toString();
    }
}
