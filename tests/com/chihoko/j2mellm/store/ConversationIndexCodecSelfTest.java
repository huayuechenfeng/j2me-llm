package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ConversationMeta;
import com.chihoko.j2mellm.model.ConversationState;

import java.io.IOException;

public final class ConversationIndexCodecSelfTest {
    public static void main(String[] args) throws Exception {
        ConversationState state = new ConversationState();
        ConversationMeta first = new ConversationMeta("chat_1", "openai");
        first.title = "第一段对话";
        first.preview = "preview";
        first.messageCount = 4;
        state.add(first);
        ConversationMeta second = new ConversationMeta("chat_2", "deepseek");
        second.title = "Second";
        state.add(second);
        state.activeConversationId = first.id;

        byte[] encoded = ConversationIndexCodec.encode(state);
        ConversationState restored = ConversationIndexCodec.decode(encoded);
        require(restored.conversations.size() == 2, "conversation count");
        require("chat_1".equals(restored.activeConversationId), "active conversation");
        require("第一段对话".equals(restored.find("chat_1").title), "UTF-8 title");
        require(restored.find("chat_1").messageCount == 4, "message count");
        require(ConversationIndexCodec.isValidRecord(encoded), "valid CRC");

        encoded[encoded.length / 2] ^= 1;
        require(!ConversationIndexCodec.isValidRecord(encoded), "corruption detected");
        boolean rejected = false;
        try { ConversationIndexCodec.decode(encoded); }
        catch (IOException expected) { rejected = true; }
        require(rejected, "corrupt record rejected");
        System.out.println("ConversationIndexCodecSelfTest passed");
    }

    private static void require(boolean value, String label) {
        if (!value) throw new RuntimeException("failed: " + label);
    }
}
