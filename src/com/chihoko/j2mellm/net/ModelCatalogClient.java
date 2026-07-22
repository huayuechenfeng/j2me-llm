package com.chihoko.j2mellm.net;

import com.chihoko.j2mellm.util.Json;
import com.chihoko.j2mellm.util.Utf8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;

/** Performs an explicit, cancellable GET of an OpenAI-compatible /models URL. */
public final class ModelCatalogClient implements Runnable {
    private String endpoint;
    private String apiKey;
    private ModelCatalogListener listener;
    private volatile boolean cancelled;
    private HttpConnection activeConnection;

    public synchronized boolean isRunning() {
        return listener != null;
    }

    public synchronized void fetch(String modelsEndpoint, String bearerKey,
                                   ModelCatalogListener callback) {
        if (callback == null) throw new NullPointerException("callback");
        if (listener != null) {
            callback.onError("已有模型列表请求正在进行");
            return;
        }
        String value = modelsEndpoint == null ? "" : modelsEndpoint.trim();
        if (value.length() == 0) {
            callback.onError("模型列表地址为空");
            return;
        }
        endpoint = value;
        apiKey = bearerKey;
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
        ModelCatalogListener callback = listener;
        try {
            ModelCatalogParser parser = execute();
            throwIfCancelled();
            Vector ids = parser.finish();
            if (!cancelled && callback != null) callback.onModels(ids, parser.isTruncated());
        } catch (Throwable failure) {
            if (!cancelled && callback != null) {
                String message = failure.getMessage();
                callback.onError(message == null ? failure.toString() : message);
            }
        } finally {
            synchronized (this) {
                activeConnection = null;
                endpoint = null;
                apiKey = null;
                listener = null;
            }
        }
    }

    private ModelCatalogParser execute() throws IOException {
        HttpConnection connection = null;
        InputStream input = null;
        try {
            throwIfCancelled();
            connection = (HttpConnection) Connector.open(endpoint, Connector.READ_WRITE, true);
            synchronized (this) { activeConnection = connection; }
            throwIfCancelled();
            connection.setRequestMethod(HttpConnection.GET);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Connection", "close");
            if (apiKey != null && apiKey.length() > 0) {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            throwIfCancelled();
            int status = connection.getResponseCode();
            throwIfCancelled();
            input = connection.openInputStream();
            throwIfCancelled();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + ": " + errorMessage(readAll(input, 16384)));
            }
            ModelCatalogParser parser = new ModelCatalogParser();
            byte[] buffer = new byte[512];
            int count;
            while (true) {
                throwIfCancelled();
                count = input.read(buffer);
                throwIfCancelled();
                if (count < 0) break;
                if (count > 0) parser.feed(buffer, 0, count);
            }
            return parser;
        } finally {
            if (input != null) try { input.close(); } catch (IOException ignored) { }
            if (connection != null) try { connection.close(); } catch (IOException ignored) { }
        }
    }

    public static String deriveModelsEndpoint(String chatEndpoint) {
        if (chatEndpoint == null) return "";
        String value = chatEndpoint.trim();
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int fragment = value.indexOf('#');
        if (fragment >= 0) value = value.substring(0, fragment);
        while (value.endsWith("/") && value.length() > 0) {
            value = value.substring(0, value.length() - 1);
        }
        String chatSuffix = "/chat/completions";
        if (value.endsWith(chatSuffix)) {
            return value.substring(0, value.length() - chatSuffix.length()) + "/models";
        }
        String responseSuffix = "/responses";
        if (value.endsWith(responseSuffix)) {
            return value.substring(0, value.length() - responseSuffix.length()) + "/models";
        }
        if (value.endsWith("/v1")) return value + "/models";
        return value + "/models";
    }

    private String readAll(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(256);
        byte[] buffer = new byte[256];
        int count;
        while (true) {
            throwIfCancelled();
            count = input.read(buffer);
            throwIfCancelled();
            if (count < 0) break;
            if (count == 0) continue;
            if (output.size() + count > limit) throw new IOException("错误响应过长");
            output.write(buffer, 0, count);
        }
        return Utf8.decode(output.toByteArray());
    }

    private void throwIfCancelled() throws IOException {
        if (cancelled) throw new IOException("请求已取消");
    }

    private String errorMessage(String body) {
        try {
            Hashtable root = Json.object(Json.parse(body));
            if (root != null) {
                Hashtable error = Json.object(root.get("error"));
                if (error != null) {
                    String message = Json.string(error.get("message"));
                    if (message != null && message.length() > 0) return message;
                }
                String message = Json.string(root.get("message"));
                if (message != null && message.length() > 0) return message;
            }
        } catch (IOException ignored) { }
        return body.length() > 240 ? body.substring(0, 240) + "..." : body;
    }
}
