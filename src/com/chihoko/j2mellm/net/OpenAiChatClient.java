package com.chihoko.j2mellm.net;

import com.chihoko.j2mellm.model.ChatMessage;
import com.chihoko.j2mellm.model.ProviderProfile;
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

public final class OpenAiChatClient implements Runnable {
    private ProviderProfile config;
    private Vector messages;
    private ChatListener listener;
    private volatile boolean cancelled;
    private HttpConnection activeConnection;
    private boolean emittedImage;

    public synchronized boolean isRunning() {
        return listener != null;
    }

    public synchronized void send(ProviderProfile provider, Vector history, ChatListener callback) {
        if (listener != null) {
            callback.onError("已有请求正在进行");
            return;
        }
        config = provider;
        messages = history;
        listener = callback;
        cancelled = false;
        emittedImage = false;
        new Thread(this).start();
    }

    public void cancel() {
        cancelled = true;
        HttpConnection connection;
        synchronized (this) {
            connection = activeConnection;
        }
        if (connection != null) {
            try { connection.close(); } catch (IOException ignored) { }
        }
    }

    public void run() {
        ChatListener callback;
        try {
            execute();
            callback = listener;
            if (!cancelled && callback != null) callback.onComplete();
        } catch (Throwable failure) {
            callback = listener;
            if (!cancelled && callback != null) {
                String message = failure.getMessage();
                callback.onError(message == null ? failure.toString() : message);
            }
        } finally {
            synchronized (this) {
                activeConnection = null;
                config = null;
                messages = null;
                listener = null;
            }
        }
    }

    private void execute() throws IOException {
        ChatRequestWriter requestWriter = new ChatRequestWriter();
        int requestLength = requestWriter.contentLength(config, messages);
        HttpConnection connection = null;
        OutputStream output = null;
        InputStream input = null;
        try {
            throwIfCancelled();
            connection = (HttpConnection) Connector.open(config.endpoint, Connector.READ_WRITE, true);
            synchronized (this) { activeConnection = connection; }
            throwIfCancelled();
            connection.setRequestMethod(HttpConnection.POST);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", config.stream
                    ? "text/event-stream, application/json" : "application/json");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("Content-Length", Integer.toString(requestLength));
            if (config.apiKey != null && config.apiKey.length() > 0) {
                connection.setRequestProperty("Authorization", "Bearer " + config.apiKey);
            }

            throwIfCancelled();
            output = new CancellationOutputStream(connection.openOutputStream());
            requestWriter.write(output, config, messages);
            throwIfCancelled();
            output.flush();
            output.close();
            output = null;
            releaseRequestImages(messages);

            throwIfCancelled();
            int status = connection.getResponseCode();
            throwIfCancelled();
            input = connection.openInputStream();
            throwIfCancelled();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + ": " + errorMessage(readAll(input, 32768)));
            }
            if (config.stream) readPossiblyStreaming(input);
            else decodeComplete(readAll(input, config.multimodal ? 524288 : 262144));
        } finally {
            releaseRequestImages(messages);
            closeQuietly(input);
            closeQuietly(output);
            if (connection != null) try { connection.close(); } catch (IOException ignored) { }
        }
    }

    private void readPossiblyStreaming(InputStream input) throws IOException {
        ByteLineReader reader = new ByteLineReader(input);
        ThinkingFilter filter = new ThinkingFilter(listener);
        String line;
        boolean sawSse = false;
        StringBuffer plainJson = new StringBuffer();
        while (!cancelled && (line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.length() == 0 || trimmed.charAt(0) == ':') continue;
            if (trimmed.startsWith("data:")) {
                sawSse = true;
                String data = trimmed.substring(5).trim();
                if ("[DONE]".equals(data)) break;
                decodeChunk(data, filter);
            } else if (!sawSse) {
                if (plainJson.length() + line.length() > 262144) {
                    throw new IOException("服务器响应超过内存安全限制");
                }
                plainJson.append(line);
            }
        }
        throwIfCancelled();
        if (!sawSse && plainJson.length() > 0) decodeComplete(plainJson.toString());
        else filter.finish();
    }

    private void decodeChunk(String json, ThinkingFilter filter) throws IOException {
        throwIfCancelled();
        Hashtable root = Json.object(Json.parse(json));
        if (root == null) return;
        throwIfError(root);
        Vector choices = Json.array(root.get("choices"));
        if (choices == null || choices.size() == 0) return;
        Hashtable choice = Json.object(choices.elementAt(0));
        Hashtable delta = choice == null ? null : Json.object(choice.get("delta"));
        if (delta == null) return;
        throwIfCancelled();
        emitImages(delta);
        String reasoning = firstString(delta, new String[] {"reasoning_content", "reasoning", "analysis"});
        if (reasoning != null && reasoning.length() > 0) listener.onReasoning(reasoning);
        String content = contentText(delta.get("content"));
        if (content != null && content.length() > 0) filter.feed(content);
    }

    private void decodeComplete(String json) throws IOException {
        throwIfCancelled();
        Hashtable root = Json.object(Json.parse(json));
        if (root == null) throw new IOException("服务器返回的不是 JSON 对象");
        throwIfError(root);
        Vector choices = Json.array(root.get("choices"));
        if (choices == null || choices.size() == 0) throw new IOException("响应中没有 choices");
        Hashtable choice = Json.object(choices.elementAt(0));
        Hashtable message = choice == null ? null : Json.object(choice.get("message"));
        if (message == null) throw new IOException("响应中没有 message");
        throwIfCancelled();
        emitImages(message);
        String reasoning = firstString(message, new String[] {"reasoning_content", "reasoning", "analysis"});
        if (reasoning != null && reasoning.length() > 0) listener.onReasoning(reasoning);
        String content = contentText(message.get("content"));
        ThinkingFilter filter = new ThinkingFilter(listener);
        if (content != null) filter.feed(content);
        filter.finish();
    }

    private void emitImages(Hashtable message) {
        if (cancelled || !config.multimodal || emittedImage || message == null) return;
        String source = sourceFrom(message.get("images"));
        if (source == null) source = sourceFrom(message.get("image_url"));
        String b64 = Json.string(message.get("b64_json"));
        if (source == null && b64 != null) source = "data:image/png;base64," + b64;
        if (source == null) {
            Vector parts = Json.array(message.get("content"));
            if (parts != null) {
                int i;
                for (i = 0; i < parts.size() && source == null; i++) {
                    Hashtable part = Json.object(parts.elementAt(i));
                    if (part == null) continue;
                    source = sourceFrom(part.get("image_url"));
                    if (source == null) source = sourceFrom(part.get("image"));
                    b64 = Json.string(part.get("b64_json"));
                    if (source == null && b64 != null) source = "data:image/png;base64," + b64;
                }
            }
        }
        if (!cancelled && source != null) {
            emittedImage = true;
            listener.onImage(source);
        }
    }

    private String sourceFrom(Object value) {
        String direct = Json.string(value);
        if (direct != null && supportedImageSource(direct)) return direct;
        Hashtable object = Json.object(value);
        if (object != null) {
            String source = sourceFrom(object.get("url"));
            if (source == null) source = sourceFrom(object.get("image_url"));
            if (source == null) source = sourceFrom(object.get("data"));
            String b64 = Json.string(object.get("b64_json"));
            if (source == null && b64 != null) source = "data:image/png;base64," + b64;
            return source;
        }
        Vector array = Json.array(value);
        if (array != null) {
            int i;
            for (i = 0; i < array.size(); i++) {
                String source = sourceFrom(array.elementAt(i));
                if (source != null) return source;
            }
        }
        return null;
    }

    private boolean supportedImageSource(String value) {
        String lower = value.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("data:image/");
    }

    private void throwIfError(Hashtable root) throws IOException {
        Hashtable error = Json.object(root.get("error"));
        if (error != null) {
            String message = Json.string(error.get("message"));
            throw new IOException(message == null ? "模型服务返回错误" : message);
        }
    }

    private String errorMessage(String body) {
        try {
            Hashtable root = Json.object(Json.parse(body));
            if (root != null) {
                Hashtable error = Json.object(root.get("error"));
                if (error != null) {
                    String message = Json.string(error.get("message"));
                    if (message != null) return message;
                }
            }
        } catch (IOException ignored) { }
        return body.length() > 240 ? body.substring(0, 240) + "…" : body;
    }

    private String firstString(Hashtable object, String[] keys) {
        int i;
        for (i = 0; i < keys.length; i++) {
            String value = Json.string(object.get(keys[i]));
            if (value != null) return value;
        }
        return null;
    }

    private String contentText(Object content) {
        String direct = Json.string(content);
        if (direct != null) return direct;
        Vector parts = Json.array(content);
        if (parts == null) return null;
        StringBuffer text = new StringBuffer();
        int i;
        for (i = 0; i < parts.size(); i++) {
            Hashtable part = Json.object(parts.elementAt(i));
            if (part == null) continue;
            String value = Json.string(part.get("text"));
            if (value == null) value = Json.string(part.get("content"));
            if (value != null) text.append(value);
        }
        return text.toString();
    }

    private void releaseRequestImages(Vector history) {
        int i;
        for (i = 0; i < history.size(); i++) {
            ((ChatMessage) history.elementAt(i)).releaseImageData();
        }
    }

    private String readAll(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while (true) {
            throwIfCancelled();
            count = input.read(buffer);
            throwIfCancelled();
            if (count < 0) break;
            if (count == 0) continue;
            if (output.size() + count > limit) throw new IOException("服务器响应超过内存安全限制");
            output.write(buffer, 0, count);
        }
        return Utf8.decode(output.toByteArray());
    }

    private void throwIfCancelled() throws IOException {
        if (cancelled) throw new IOException("request cancelled");
    }

    /** Checks cancellation before every at-most-512-byte network write. */
    private final class CancellationOutputStream extends OutputStream {
        private final OutputStream delegate;

        CancellationOutputStream(OutputStream target) {
            delegate = target;
        }

        public void write(int value) throws IOException {
            throwIfCancelled();
            delegate.write(value);
        }

        public void write(byte[] buffer, int offset, int length) throws IOException {
            while (length > 0) {
                throwIfCancelled();
                int count = length > 512 ? 512 : length;
                delegate.write(buffer, offset, count);
                offset += count;
                length -= count;
            }
        }

        public void flush() throws IOException {
            throwIfCancelled();
            delegate.flush();
        }

        public void close() throws IOException {
            delegate.close();
        }
    }

    private void closeQuietly(InputStream input) {
        if (input != null) try { input.close(); } catch (IOException ignored) { }
    }

    private void closeQuietly(OutputStream output) {
        if (output != null) try { output.close(); } catch (IOException ignored) { }
    }
}
