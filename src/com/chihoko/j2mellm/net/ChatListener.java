package com.chihoko.j2mellm.net;

public interface ChatListener {
    void onContent(String text);
    void onReasoning(String text);
    void onImage(String source);
    void onComplete();
    void onError(String message);
}

