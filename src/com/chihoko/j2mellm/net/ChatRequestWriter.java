

package com.chihoko.j2mellm.net;

import com.chihoko.j2mellm.model.ChatMessage;
import com.chihoko.j2mellm.model.ProviderPresets;
import com.chihoko.j2mellm.model.ProviderProfile;
import com.chihoko.j2mellm.util.JsonStreamWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Vector;

/** Writes a Chat Completions request without materialising the whole JSON body. */
final class ChatRequestWriter {
    int contentLength(ProviderProfile profile, Vector history) throws IOException {
        JsonStreamWriter writer = new JsonStreamWriter(null);
        writeBody(writer, profile, history);
        writer.finish();
        return writer.size();
    }

    void write(OutputStream output, ProviderProfile profile, Vector history) throws IOException {
        JsonStreamWriter writer = new JsonStreamWriter(output);
        writeBody(writer, profile, history);
        writer.finish();
    }

    private void writeBody(JsonStreamWriter json, ProviderProfile profile, Vector history)
            throws IOException {
        json.raw("{\"model\":");
        json.quoted(profile.model);
        json.raw(",\"messages\":[");
        boolean comma = false;
        String system = profile.systemPrompt == null ? "" : profile.systemPrompt.trim();
        if (system.length() > 0) {
            writeTextMessage(json, "system", profile.systemPrompt, null, false);
            comma = true;
        }

        int maximum = profile.historyMessages;
        if (maximum < 2) maximum = 2;
        int start = history.size();
        int selected = 0;
        int i;
        for (i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = (ChatMessage) history.elementAt(i);
            if (message.pending || message.error) continue;
            start = i;
            selected++;
            if (selected == maximum) break;
        }
        for (i = start; i < history.size(); i++) {
            ChatMessage message = (ChatMessage) history.elementAt(i);
            if (message.pending || message.error) continue;
            if (comma) json.raw(",");
            writeMessage(json, profile, message);
            comma = true;
        }
        json.raw("],\"stream\":");
        json.raw(profile.stream ? "true" : "false");
        writeThinking(json, profile);
        json.raw("}");
    }

    private void writeMessage(JsonStreamWriter json, ProviderProfile profile,
            ChatMessage message) throws IOException {
        byte[] image = profile.multimodal ? message.getImageData() : null;
        if (ChatMessage.ROLE_USER.equals(message.role) && image != null && image.length > 0) {
            String mime = message.getImageMime();
            if (mime == null || mime.length() == 0) mime = "image/jpeg";
            json.raw("{\"role\":");
            json.quoted(message.role);
            json.raw(",\"content\":[{\"type\":\"text\",\"text\":");
            json.quoted(message.getContent());
            json.raw("},{\"type\":\"image_url\",\"image_url\":{\"url\":\"");
            json.raw("data:");
            json.raw(safeMime(mime));
            json.raw(";base64,");
            json.base64(image);
            json.raw("\",\"detail\":\"low\"}}]}");
            return;
        }

        String reasoning = null;
        if (ChatMessage.ROLE_ASSISTANT.equals(message.role)
                && effectiveThinkingProtocol(profile) == ProviderProfile.THINKING_PROTOCOL_KIMI) {
            reasoning = message.getReasoning();
        }
        writeTextMessage(json, message.role, message.getContent(), reasoning, true);
    }

    private void writeTextMessage(JsonStreamWriter json, String role, String content,
            String reasoning, boolean includeReasoning) throws IOException {
        json.raw("{\"role\":");
        json.quoted(role);
        json.raw(",\"content\":");
        json.quoted(content);
        if (includeReasoning && reasoning != null && reasoning.length() > 0) {
            json.raw(",\"reasoning_content\":");
            json.quoted(reasoning);
        }
        json.raw("}");
    }

    private void writeThinking(JsonStreamWriter json, ProviderProfile profile) throws IOException {
        int protocol = effectiveThinkingProtocol(profile);
        int mode = profile.thinkingMode;
        if (mode == ProviderProfile.THINKING_AUTO || protocol == ProviderProfile.THINKING_PROTOCOL_NONE) {
            return;
        }
        if (protocol == ProviderProfile.THINKING_PROTOCOL_KIMI) mode = ProviderProfile.THINKING_ON;
        if (protocol == ProviderProfile.THINKING_PROTOCOL_OPENAI_EFFORT) {
            json.raw(",\"reasoning_effort\":");
            json.quoted(mode == ProviderProfile.THINKING_OFF ? "none" : effort(profile.reasoningEffort));
        } else if (protocol == ProviderProfile.THINKING_PROTOCOL_ENABLED_OBJECT) {
            json.raw(",\"thinking\":{\"type\":");
            json.quoted(mode == ProviderProfile.THINKING_OFF ? "disabled" : "enabled");
            json.raw("}");
            if (mode == ProviderProfile.THINKING_ON
                    && !ProviderPresets.isKimiThinkingObjectModel(profile)) {
                json.raw(",\"reasoning_effort\":");
                json.quoted(effort(profile.reasoningEffort));
            }
        } else if (protocol == ProviderProfile.THINKING_PROTOCOL_KIMI
                && ProviderPresets.usesKimiReasoningEffort(profile)) {
            json.raw(",\"reasoning_effort\":");
            json.quoted(kimiEffort(profile.reasoningEffort));
        }
    }

    private int effectiveThinkingProtocol(ProviderProfile profile) {
        int protocol = profile.thinkingProtocol;
        if (ProviderPresets.KIMI.equals(profile.presetId)) {
            if (ProviderPresets.isKimiAlwaysThinking(profile)) {
                protocol = ProviderProfile.THINKING_PROTOCOL_KIMI;
            } else if (ProviderPresets.isKimiThinkingObjectModel(profile)) {
                protocol = ProviderProfile.THINKING_PROTOCOL_ENABLED_OBJECT;
            } else {
                protocol = ProviderProfile.THINKING_PROTOCOL_NONE;
            }
        }
        return protocol;
    }

    private String effort(String value) {
        return ThinkingRequestPolicy.supportedEffort(value) ? value : "low";
    }

    private String kimiEffort(String value) {
        return "low".equals(value) || "high".equals(value) || "max".equals(value)
                ? value : "low";
    }

    private String safeMime(String value) {
        int i;
        for (i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean safe = ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z'
                    || ch >= '0' && ch <= '9' || ch == '/' || ch == '-' || ch == '+' || ch == '.';
            if (!safe) return "image/jpeg";
        }
        return value;
    }
}


