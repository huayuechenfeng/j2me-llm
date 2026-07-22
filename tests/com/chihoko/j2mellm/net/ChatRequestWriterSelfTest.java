

package com.chihoko.j2mellm.net;

import com.chihoko.j2mellm.model.ChatMessage;
import com.chihoko.j2mellm.model.ImageAttachment;
import com.chihoko.j2mellm.model.ProviderPresets;
import com.chihoko.j2mellm.model.ProviderProfile;
import com.chihoko.j2mellm.util.Utf8;

import java.io.ByteArrayOutputStream;
import java.util.Vector;

public final class ChatRequestWriterSelfTest {
    public static void main(String[] args) throws Exception {
        testsLengthThinkingAndLazyMedia();
        testsKimiReasoningHistory();
        testsCustomKimiReasoningHistory();
        testsKimiModelSpecificProtocols();
        testsEligibleHistoryWindow();
        System.out.println("ChatRequestWriterSelfTest passed");
    }

    private static void testsLengthThinkingAndLazyMedia() throws Exception {
        ProviderProfile profile = ProviderPresets.create(ProviderPresets.OPENAI);
        profile.model = "gpt-test";
        profile.systemPrompt = "";
        profile.thinkingMode = ProviderProfile.THINKING_OFF;
        ChatMessage user = new ChatMessage(ChatMessage.ROLE_USER, "看图");
        user.setAttachment(new ImageAttachment("x.png", "image/png", new byte[] {1, 2, 3, 4}));
        Vector history = new Vector();
        history.addElement(user);

        String withoutMedia = write(profile, history);
        contains(withoutMedia, "\"reasoning_effort\":\"none\"", "OpenAI thinking off");
        excludes(withoutMedia, "image_url", "multimodal default lazy");

        profile.multimodal = true;
        String withMedia = write(profile, history);
        contains(withMedia, "data:image/png;base64,AQIDBA==", "streaming image base64");

        profile = ProviderPresets.create(ProviderPresets.DEEPSEEK);
        profile.thinkingMode = ProviderProfile.THINKING_ON;
        String deepseek = write(profile, new Vector());
        contains(deepseek, "\"thinking\":{\"type\":\"enabled\"}", "DeepSeek thinking object");
        contains(deepseek, "\"reasoning_effort\":\"high\"", "DeepSeek effort");
    }

    private static void testsKimiReasoningHistory() throws Exception {
        ProviderProfile profile = ProviderPresets.create(ProviderPresets.KIMI);
        profile.thinkingMode = ProviderProfile.THINKING_OFF;
        ChatMessage assistant = new ChatMessage(ChatMessage.ROLE_ASSISTANT, "答案");
        assistant.appendReasoning("保留推理上下文");
        Vector history = new Vector();
        history.addElement(assistant);
        String json = write(profile, history);
        contains(json, "\"reasoning_content\":\"保留推理上下文\"", "Kimi reasoning history");
        contains(json, "\"reasoning_effort\":\"low\"", "Kimi K3 cannot disable");
        excludes(json, "\"type\":\"disabled\"", "no invalid Kimi disable");
    }

    private static void testsCustomKimiReasoningHistory() throws Exception {
        ProviderProfile profile = ProviderPresets.create(ProviderPresets.CUSTOM);
        profile.model = "always-thinking-model";
        profile.thinkingProtocol = ProviderProfile.THINKING_PROTOCOL_KIMI;
        profile.thinkingMode = ProviderProfile.THINKING_OFF;
        ChatMessage assistant = new ChatMessage(ChatMessage.ROLE_ASSISTANT, "custom answer");
        assistant.appendReasoning("custom preserved reasoning");
        Vector history = new Vector();
        history.addElement(assistant);

        String json = write(profile, history);
        contains(json, "\"reasoning_content\":\"custom preserved reasoning\"",
                "custom Kimi reasoning history");
        contains(json, "\"reasoning_effort\":\"low\"", "custom Kimi cannot disable");

        profile.thinkingProtocol = ProviderProfile.THINKING_PROTOCOL_ENABLED_OBJECT;
        json = write(profile, history);
        excludes(json, "reasoning_content", "non-Kimi custom omits reasoning history");
    }

    private static void testsKimiModelSpecificProtocols() throws Exception {
        ProviderProfile profile = ProviderPresets.create(ProviderPresets.KIMI);
        profile.model = "kimi-k2.7-code-highspeed";
        profile.thinkingMode = ProviderProfile.THINKING_OFF;
        ChatMessage assistant = new ChatMessage(ChatMessage.ROLE_ASSISTANT, "code answer");
        assistant.appendReasoning("preserved code reasoning");
        Vector history = new Vector();
        history.addElement(assistant);
        String json = write(profile, history);
        contains(json, "\"reasoning_content\":\"preserved code reasoning\"",
                "Kimi K2.7 preserves reasoning");
        excludes(json, "reasoning_effort", "Kimi K2.7 rejects effort");
        excludes(json, "\"thinking\"", "Kimi K2.7 needs no thinking switch");

        profile.model = "kimi-k2.6";
        profile.thinkingMode = ProviderProfile.THINKING_ON;
        json = write(profile, new Vector());
        contains(json, "\"thinking\":{\"type\":\"enabled\"}", "Kimi K2.6 thinking on");
        excludes(json, "reasoning_effort", "Kimi K2.6 rejects effort");

        profile.thinkingMode = ProviderProfile.THINKING_OFF;
        json = write(profile, new Vector());
        contains(json, "\"thinking\":{\"type\":\"disabled\"}", "Kimi K2.6 thinking off");

        profile.model = "moonshot-v1-8k";
        profile.thinkingMode = ProviderProfile.THINKING_ON;
        json = write(profile, new Vector());
        excludes(json, "\"thinking\"", "unknown Kimi model gets no thinking field");
        excludes(json, "reasoning_effort", "unknown Kimi model gets no effort");
    }

    private static void testsEligibleHistoryWindow() throws Exception {
        ProviderProfile profile = ProviderPresets.create(ProviderPresets.CUSTOM);
        profile.model = "history-test";
        profile.systemPrompt = "";
        profile.historyMessages = 3;
        Vector history = new Vector();
        history.addElement(new ChatMessage(ChatMessage.ROLE_USER, "eligible-old"));
        ChatMessage pendingFirst = new ChatMessage(ChatMessage.ROLE_ASSISTANT, "pending-first");
        pendingFirst.pending = true;
        history.addElement(pendingFirst);
        history.addElement(new ChatMessage(ChatMessage.ROLE_USER, "eligible-one"));
        ChatMessage failed = new ChatMessage(ChatMessage.ROLE_ASSISTANT, "failed-message");
        failed.error = true;
        history.addElement(failed);
        history.addElement(new ChatMessage(ChatMessage.ROLE_ASSISTANT, "eligible-two"));
        ChatMessage pendingLast = new ChatMessage(ChatMessage.ROLE_USER, "pending-last");
        pendingLast.pending = true;
        history.addElement(pendingLast);
        history.addElement(new ChatMessage(ChatMessage.ROLE_USER, "eligible-three"));

        String json = write(profile, history);
        excludes(json, "eligible-old", "older eligible message excluded");
        contains(json, "eligible-one", "third newest eligible message retained");
        contains(json, "eligible-two", "second newest eligible message retained");
        contains(json, "eligible-three", "newest eligible message retained");
        excludes(json, "pending-first", "pending message excluded");
        excludes(json, "pending-last", "trailing pending message excluded");
        excludes(json, "failed-message", "error message excluded");
        require(json.indexOf("eligible-one") < json.indexOf("eligible-two")
                && json.indexOf("eligible-two") < json.indexOf("eligible-three"),
                "eligible history order");
    }

    private static String write(ProviderProfile profile, Vector history) throws Exception {
        ChatRequestWriter request = new ChatRequestWriter();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        request.write(output, profile, history);
        require(request.contentLength(profile, history) == output.size(), "Content-Length");
        return Utf8.decode(output.toByteArray());
    }

    private static void contains(String value, String part, String label) {
        require(value.indexOf(part) >= 0, label);
    }

    private static void excludes(String value, String part, String label) {
        require(value.indexOf(part) < 0, label);
    }

    private static void require(boolean value, String label) {
        if (!value) throw new RuntimeException("failed: " + label);
    }
}


