package com.chihoko.j2mellm.provision;

import com.chihoko.j2mellm.model.ProfileState;
import com.chihoko.j2mellm.model.ProviderPresets;
import com.chihoko.j2mellm.model.ProviderProfile;
import com.chihoko.j2mellm.model.SearchConfig;

import java.io.IOException;
import java.util.Vector;

/** Maps the transport format to the four fixed RMS profile slots. */
public final class ProvisioningMapper {
    private ProvisioningMapper() { }

    public static ProvisioningPackage exportProfiles(ProfileState state) {
        return exportConfiguration(state, null);
    }

    public static ProvisioningPackage exportConfiguration(ProfileState state,
            SearchConfig searchConfig) {
        ProvisioningPackage result = new ProvisioningPackage();
        result.setActiveProfileId(state.activeProfileId);
        int i;
        for (i = 0; i < state.profiles.size(); i++) {
            ProviderProfile source = (ProviderProfile) state.profiles.elementAt(i);
            ProvisioningProfile target = new ProvisioningProfile();
            target.id = source.id;
            target.preset = source.presetId;
            target.name = source.name;
            target.endpoint = source.endpoint;
            target.modelsEndpoint = source.modelsEndpoint;
            target.apiKey = source.apiKey;
            target.model = source.model;
            target.systemPrompt = source.systemPrompt;
            target.stream = source.stream;
            target.historyMessages = source.historyMessages;
            target.thinkingMode = source.thinkingMode;
            target.thinkingProtocol = source.thinkingProtocol;
            target.reasoningEffort = source.reasoningEffort;
            target.multimodal = source.multimodal;
            target.endpointOverridden = source.endpointOverride;
            result.addProfile(target);
        }
        if (searchConfig != null) result.setSearchConfig(searchConfig);
        return result;
    }

    public static ProfileState importProfiles(ProvisioningPackage source, ProfileState previous)
            throws IOException {
        if (source == null) throw new IOException("配置包为空");
        ProfileState result = copyPrevious(previous);
        Vector profiles = source.getProfiles();
        int i;
        for (i = 0; i < profiles.size(); i++) {
            ProvisioningProfile incoming = (ProvisioningProfile) profiles.elementAt(i);
            String id = fixedId(incoming);
            ProviderProfile target = ProviderPresets.create(id);
            ProviderProfile old = previous == null ? null : previous.find(id);
            if (incoming.name != null && incoming.name.length() > 0) target.name = incoming.name;
            target.apiKey = safe(incoming.apiKey);
            target.model = safe(incoming.model);
            target.systemPrompt = safe(incoming.systemPrompt);
            target.stream = incoming.stream;
            target.historyMessages = clamp(incoming.historyMessages, 2, 256, 12);
            target.thinkingMode = clamp(incoming.thinkingMode,
                    ProviderProfile.THINKING_AUTO, ProviderProfile.THINKING_OFF,
                    ProviderProfile.THINKING_AUTO);
            target.reasoningEffort = validEffort(incoming.reasoningEffort)
                    ? incoming.reasoningEffort : target.reasoningEffort;
            target.multimodal = incoming.multimodal;
            target.reasoningExpanded = old != null && old.reasoningExpanded;

            if (ProviderPresets.CUSTOM.equals(id)) {
                target.endpointOverride = true;
                target.endpoint = safe(incoming.endpoint);
                target.modelsEndpoint = safe(incoming.modelsEndpoint);
                target.thinkingProtocol = clamp(incoming.thinkingProtocol,
                        ProviderProfile.THINKING_PROTOCOL_NONE,
                        ProviderProfile.THINKING_PROTOCOL_KIMI,
                        ProviderProfile.THINKING_PROTOCOL_NONE);
            } else if (incoming.endpointOverridden) {
                target.endpointOverride = true;
                target.endpoint = safe(incoming.endpoint);
                target.modelsEndpoint = safe(incoming.modelsEndpoint);
            }
            if (target.modelsEndpoint.length() == 0) {
                target.modelsEndpoint = ProviderPresets.deriveModelsEndpoint(target.endpoint);
            }
            if (ProviderPresets.isKimiAlwaysThinking(target)
                    && target.thinkingMode == ProviderProfile.THINKING_OFF) {
                target.thinkingMode = ProviderProfile.THINKING_ON;
            }
            if (target.model.length() > 0) target.addCachedModel(target.model);
            result.replace(target);
        }

        ProviderPresets.ensureFixedProfiles(result);
        String wanted = source.getActiveProfileId();
        if (wanted != null && wanted.length() > 0) {
            String mappedActive = mapActiveId(wanted, profiles);
            if (result.find(mappedActive) != null) result.activeProfileId = mappedActive;
        }
        if (result.find(result.activeProfileId) == null) {
            result.activeProfileId = ProviderPresets.OPENAI;
        }
        return result;
    }

    /** An omitted search object deliberately preserves the device's current setting. */
    public static SearchConfig importSearch(ProvisioningPackage source, SearchConfig previous)
            throws IOException {
        if (source == null) throw new IOException("配置包为空");
        if (!source.hasSearchConfig()) {
            return previous == null ? new SearchConfig() : previous.copy();
        }
        SearchConfig result = source.getSearchConfig();
        result.normalize();
        return result;
    }

    /**
     * Start from detached copies of the persisted profiles. A configuration
     * package may intentionally contain only one profile; profiles omitted from
     * that package must keep their existing keys, settings and model cache.
     */
    private static ProfileState copyPrevious(ProfileState previous) {
        ProfileState result = ProviderPresets.createDefaultState();
        if (previous == null) return result;
        result.profiles.removeAllElements();
        int i;
        for (i = 0; i < previous.profiles.size(); i++) {
            ProviderProfile profile = (ProviderProfile) previous.profiles.elementAt(i);
            result.profiles.addElement(profile.copy());
        }
        result.activeProfileId = previous.activeProfileId;
        result.legacyMigrated = previous.legacyMigrated;
        result.recoveredFromBackup = previous.recoveredFromBackup;
        result.storageCorrupt = previous.storageCorrupt;
        ProviderPresets.ensureFixedProfiles(result);
        return result;
    }

    private static String mapActiveId(String wanted, Vector incomingProfiles) {
        int i;
        for (i = 0; i < incomingProfiles.size(); i++) {
            ProvisioningProfile profile = (ProvisioningProfile) incomingProfiles.elementAt(i);
            if (safe(profile.id).equals(wanted)) return fixedId(profile);
        }
        return isFixed(wanted) ? wanted : ProviderPresets.OPENAI;
    }

    private static String fixedId(ProvisioningProfile profile) {
        if (isFixed(profile.id)) return profile.id;
        if (isFixed(profile.preset)) return profile.preset;
        return ProviderPresets.CUSTOM;
    }

    private static boolean isFixed(String value) {
        return ProviderPresets.OPENAI.equals(value) || ProviderPresets.DEEPSEEK.equals(value)
                || ProviderPresets.KIMI.equals(value) || ProviderPresets.CUSTOM.equals(value);
    }

    private static int clamp(int value, int minimum, int maximum, int fallback) {
        return value < minimum || value > maximum ? fallback : value;
    }

    private static boolean validEffort(String value) {
        return "minimal".equals(value) || "low".equals(value) || "medium".equals(value)
                || "high".equals(value) || "xhigh".equals(value) || "max".equals(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
