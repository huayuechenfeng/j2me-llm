

package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.i18n.I18n;
import com.chihoko.j2mellm.i18n.TextId;
import com.chihoko.j2mellm.model.ProviderPresets;
import com.chihoko.j2mellm.model.ProviderProfile;

import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;

/** Edits one provider profile without changing its independent chat history. */
public final class SettingsForm extends Form {
    private static final String[] EFFORT_LABELS = {
            "minimal", "low", "medium", "high", "xhigh", "max"
    };

    public final Command saveCommand = new Command(I18n.text(TextId.SAVE), Command.OK, 1);
    public final Command modelsCommand = new Command(
            I18n.text(TextId.SELECT_MODEL), Command.SCREEN, 2);
    public final Command backCommand = new Command(I18n.text(TextId.BACK), Command.BACK, 3);

    private final TextField nameField;
    private final TextField endpointField;
    private final TextField modelsEndpointField;
    private final TextField keyField;
    private final TextField modelField;
    private final TextField systemField;
    private final ChoiceGroup endpointOverrideChoice;
    private final ChoiceGroup streamChoice;
    private final ChoiceGroup thinkingChoice;
    private final ChoiceGroup effortChoice;
    private final ChoiceGroup protocolChoice;
    private final ChoiceGroup multimodalChoice;
    private final TextField historyField;
    private final boolean customProfile;
    private final String originalName;
    private final String displayedName;

    public SettingsForm(ProviderProfile profile, CommandListener listener) {
        super(I18n.text(TextId.PROFILE_SETTINGS_PREFIX) + I18n.profileName(profile));
        customProfile = ProviderPresets.CUSTOM.equals(profile.presetId);
        originalName = safe(profile.name);
        displayedName = I18n.profileName(profile);

        nameField = new TextField(I18n.text(TextId.PROFILE_NAME), displayedName,
                ProviderProfile.MAX_NAME_CHARS, TextField.ANY);
        endpointOverrideChoice = new ChoiceGroup(I18n.text(TextId.ENDPOINT_CONTROL),
                ChoiceGroup.MULTIPLE,
                new String[] {I18n.text(TextId.ADVANCED_OVERRIDE_GATEWAY)}, null);
        endpointOverrideChoice.setSelectedIndex(0, customProfile || profile.endpointOverride);
        endpointField = new TextField(I18n.text(TextId.CHAT_ENDPOINT), safe(profile.endpoint),
                ProviderProfile.MAX_ENDPOINT_CHARS, TextField.URL);
        modelsEndpointField = new TextField(I18n.text(TextId.MODELS_ENDPOINT),
                safe(profile.modelsEndpoint),
                ProviderProfile.MAX_ENDPOINT_CHARS, TextField.URL);
        keyField = new TextField(I18n.text(TextId.API_KEY_OPTIONAL), safe(profile.apiKey),
                ProviderProfile.MAX_API_KEY_CHARS,
                TextField.ANY | TextField.PASSWORD | TextField.SENSITIVE);
        modelField = new TextField(I18n.text(TextId.MODEL_NAME), safe(profile.model),
                ProviderProfile.MAX_MODEL_CHARS, TextField.ANY);
        systemField = new TextField(I18n.text(TextId.SYSTEM_PROMPT),
                safe(profile.systemPrompt), ProviderProfile.MAX_SYSTEM_PROMPT_CHARS,
                TextField.ANY);

        streamChoice = new ChoiceGroup(I18n.text(TextId.RESPONSE_MODE),
                ChoiceGroup.MULTIPLE,
                new String[] {I18n.text(TextId.ENABLE_STREAMING)}, null);
        streamChoice.setSelectedIndex(0, profile.stream);

        String[] thinkingLabels = {
                I18n.text(TextId.THINKING_AUTO),
                I18n.text(TextId.THINKING_ENABLED),
                I18n.text(TextId.THINKING_DISABLED)
        };
        thinkingChoice = new ChoiceGroup(I18n.text(TextId.THINKING_MODE),
                ChoiceGroup.EXCLUSIVE, thinkingLabels, null);
        int displayedThinkingMode = normalizeThinkingMode(profile.thinkingMode);
        if (ProviderPresets.isKimiAlwaysThinking(profile)
                && displayedThinkingMode == ProviderProfile.THINKING_OFF) {
            displayedThinkingMode = ProviderProfile.THINKING_ON;
        }
        thinkingChoice.setSelectedIndex(displayedThinkingMode, true);

        effortChoice = new ChoiceGroup(I18n.text(TextId.REASONING_EFFORT),
                ChoiceGroup.POPUP,
                EFFORT_LABELS, null);
        effortChoice.setSelectedIndex(find(EFFORT_LABELS, profile.reasoningEffort, 1), true);

        if (customProfile) {
            String[] protocolLabels = {
                    I18n.text(TextId.NO_THINKING_PARAMETER),
                    "OpenAI reasoning_effort",
                    "thinking enabled/disabled",
                    I18n.text(TextId.KIMI_ALWAYS_THINKING)
            };
            protocolChoice = new ChoiceGroup(I18n.text(TextId.THINKING_PROTOCOL),
                    ChoiceGroup.POPUP, protocolLabels, null);
            protocolChoice.setSelectedIndex(normalizeProtocol(profile.thinkingProtocol), true);
        } else {
            protocolChoice = null;
        }

        multimodalChoice = new ChoiceGroup(I18n.text(TextId.MULTIMODAL),
                ChoiceGroup.MULTIPLE,
                new String[] {I18n.text(TextId.ALLOW_IMAGES)}, null);
        multimodalChoice.setSelectedIndex(0, profile.multimodal);
        historyField = new TextField(I18n.text(TextId.HISTORY_MESSAGES),
                Integer.toString(profile.historyMessages), 2, TextField.NUMERIC);

        append(nameField);
        append(endpointOverrideChoice);
        append(endpointField);
        append(modelsEndpointField);
        append(keyField);
        append(modelField);
        append(systemField);
        append(streamChoice);
        append(thinkingChoice);
        append(effortChoice);
        if (protocolChoice != null) append(protocolChoice);
        append(multimodalChoice);
        append(historyField);

        if (ProviderPresets.isKimiAlwaysThinking(profile)) {
            append(new StringItem(I18n.text(TextId.ALWAYS_THINKING_MODEL),
                    I18n.text(TextId.ALWAYS_THINKING_HELP)));
        }
        append(new StringItem(I18n.text(TextId.ADVANCED_OVERRIDE),
                customProfile
                        ? I18n.text(TextId.CUSTOM_OVERRIDE_HELP)
                        : I18n.text(TextId.OFFICIAL_OVERRIDE_HELP)));
        append(new StringItem(I18n.text(TextId.MEMORY_HINT),
                I18n.text(TextId.MEMORY_HINT_BODY)));
        append(new StringItem(I18n.text(TextId.SECURITY_HINT),
                I18n.text(TextId.SECURITY_HINT_BODY)));

        addCommand(saveCommand);
        addCommand(modelsCommand);
        addCommand(backCommand);
        setCommandListener(listener);
    }

    /** Copies form values while deliberately preserving reasoningExpanded. */
    public void copyTo(ProviderProfile profile) {
        String editedName = nameField.getString().trim();
        profile.name = editedName.equals(displayedName) ? originalName : editedName;
        profile.endpointOverride = customProfile || endpointOverrideChoice.isSelected(0);
        if (profile.endpointOverride) {
            profile.endpoint = endpointField.getString().trim();
            profile.modelsEndpoint = modelsEndpointField.getString().trim();
        } else {
            ProviderProfile defaults = ProviderPresets.create(profile.presetId);
            profile.endpoint = defaults.endpoint;
            profile.modelsEndpoint = defaults.modelsEndpoint;
        }
        if (profile.modelsEndpoint.length() == 0) {
            profile.modelsEndpoint = ProviderPresets.deriveModelsEndpoint(profile.endpoint);
        }

        profile.apiKey = keyField.getString().trim();
        profile.model = modelField.getString().trim();
        profile.systemPrompt = systemField.getString();
        profile.stream = streamChoice.isSelected(0);
        if (protocolChoice != null) {
            profile.thinkingProtocol = protocolChoice.getSelectedIndex();
        }

        int thinkingMode = thinkingChoice.getSelectedIndex();
        if (thinkingMode < ProviderProfile.THINKING_AUTO
                || thinkingMode > ProviderProfile.THINKING_OFF) {
            thinkingMode = ProviderProfile.THINKING_AUTO;
        }
        if (ProviderPresets.isKimiAlwaysThinking(profile)
                && thinkingMode == ProviderProfile.THINKING_OFF) {
            thinkingMode = ProviderProfile.THINKING_ON;
        }
        profile.thinkingMode = thinkingMode;
        profile.reasoningEffort = EFFORT_LABELS[effortChoice.getSelectedIndex()];
        profile.multimodal = multimodalChoice.isSelected(0);

        int history = 12;
        try {
            history = Integer.parseInt(historyField.getString());
        } catch (NumberFormatException ignored) {
        }
        if (history < 2) history = 2;
        if (history > 24) history = 24;
        profile.historyMessages = history;
    }

    private static int normalizeThinkingMode(int value) {
        if (value < ProviderProfile.THINKING_AUTO || value > ProviderProfile.THINKING_OFF) {
            return ProviderProfile.THINKING_AUTO;
        }
        return value;
    }

    private static int normalizeProtocol(int value) {
        if (value < ProviderProfile.THINKING_PROTOCOL_NONE
                || value > ProviderProfile.THINKING_PROTOCOL_KIMI) {
            return ProviderProfile.THINKING_PROTOCOL_NONE;
        }
        return value;
    }

    private static int find(String[] values, String wanted, int fallback) {
        int i;
        for (i = 0; i < values.length; i++) {
            if (values[i].equals(wanted)) return i;
        }
        return fallback;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}


