package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ChatMessage;
import com.chihoko.j2mellm.model.ResourceLimits;
import com.chihoko.j2mellm.model.SearchBundle;
import com.chihoko.j2mellm.model.SearchResult;

import java.io.IOException;

public final class ConversationMessageCodecSelfTest {
    public static void main(String[] args) throws Exception {
        ResourceLimits limits = ResourceLimits.recommended();
        limits.mode = ResourceLimits.MODE_CUSTOM;
        limits.messageContentChars = 262144;
        limits.messageReasoningChars = 131072;
        ChatMessage.configureLimits(limits);

        String longText = repeat('长', 70000);
        ChatMessage source = new ChatMessage(ChatMessage.ROLE_USER, longText);
        SearchBundle search = new SearchBundle("查询", "test-provider");
        search.add(new SearchResult("标题", "https://example.test", "摘要"));
        source.setSearchBundle(search);
        byte[] encoded = ConversationMessageCodec.encode(source);
        ChatMessage restored = ConversationMessageCodec.decode(encoded);
        require(restored.getContent().length() == 70000, "long text beyond writeUTF");
        require(restored.getSearchBundle() != null, "search bundle");
        require(restored.getSearchBundle().results.size() == 1, "search result");
        require(ConversationMessageCodec.isValid(encoded), "valid CRC");

        encoded[20] ^= 2;
        require(!ConversationMessageCodec.isValid(encoded), "corruption detected");
        boolean rejected = false;
        try { ConversationMessageCodec.decode(encoded); }
        catch (IOException expected) { rejected = true; }
        require(rejected, "corrupt record rejected");
        ChatMessage.configureLimits(ResourceLimits.recommended());
        System.out.println("ConversationMessageCodecSelfTest passed");
    }

    private static String repeat(char value, int count) {
        StringBuffer result = new StringBuffer(count);
        int i;
        for (i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static void require(boolean value, String label) {
        if (!value) throw new RuntimeException("failed: " + label);
    }
}
