package com.chihoko.j2mellm.util;

import java.util.Hashtable;
import java.util.Vector;

public final class JsonSelfTest {
    public static void main(String[] args) throws Exception {
        String source = "{\"choices\":[{\"delta\":{\"content\":\"你好\\nworld\",\"reasoning_content\":\"思考\"}}],\"ok\":true}";
        Hashtable root = Json.object(Json.parse(source));
        require(root != null, "root object");
        Vector choices = Json.array(root.get("choices"));
        require(choices != null && choices.size() == 1, "choices array");
        Hashtable choice = Json.object(choices.elementAt(0));
        Hashtable delta = Json.object(choice.get("delta"));
        require("你好\nworld".equals(Json.string(delta.get("content"))), "content decoding");
        require("思考".equals(Json.string(delta.get("reasoning_content"))), "reasoning decoding");

        String escaped = Json.quote("a\"b\\c\n中文");
        require("a\"b\\c\n中文".equals(Json.string(Json.parse(escaped))), "quote round trip");

        String unicode = "J2ME 对话 😀";
        require(unicode.equals(Utf8.decode(Utf8.encode(unicode))), "UTF-8 round trip");
        System.out.println("JsonSelfTest passed");
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new RuntimeException("failed: " + name);
    }
}

