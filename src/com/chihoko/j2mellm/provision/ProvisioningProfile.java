package com.chihoko.j2mellm.provision;

/**
 * Provider-neutral transfer object used by .j2cfg import and export.
 * It deliberately does not depend on the RMS profile implementation.
 */
public final class ProvisioningProfile {
    public String id = "";
    public String preset = "custom";
    public String name = "";
    public String endpoint = "";
    public String modelsEndpoint = "";
    public String apiKey = "";
    public String model = "";
    public String systemPrompt = "";
    public boolean stream = true;
    public int historyMessages = 8;
    public int thinkingMode;
    public int thinkingProtocol;
    public String reasoningEffort = "";
    public boolean multimodal;
    public boolean endpointOverridden;
}
