package com.chihoko.j2mellm.model;

public final class ProviderConfig {
    public String name = "OpenAI Compatible";
    public String endpoint = "https://api.openai.com/v1/chat/completions";
    public String apiKey = "";
    public String model = "";
    public String systemPrompt = "You are a helpful assistant.";
    public boolean stream = true;
    public int historyMessages = 12;

    public boolean isReady() {
        return endpoint != null && endpoint.trim().length() > 0
                && model != null && model.trim().length() > 0;
    }

    public String displayName() {
        if (model != null && model.trim().length() > 0) {
            return model.trim();
        }
        if (name != null && name.trim().length() > 0) {
            return name.trim();
        }
        return "J2ME LLM";
    }
}

