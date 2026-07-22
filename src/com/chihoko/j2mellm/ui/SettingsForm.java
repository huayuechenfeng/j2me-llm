

package com.chihoko.j2mellm.ui;

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
    private static final String[] THINKING_LABELS = {
            "自动（不发送控制参数）", "开启", "关闭"
    };
    private static final String[] EFFORT_LABELS = {
            "minimal", "low", "medium", "high", "xhigh", "max"
    };
    private static final String[] PROTOCOL_LABELS = {
            "不发送思考参数", "OpenAI reasoning_effort",
            "thinking enabled/disabled", "始终思考（Kimi）"
    };

    public final Command saveCommand = new Command("保存", Command.OK, 1);
    public final Command modelsCommand = new Command("选择模型", Command.SCREEN, 2);
    public final Command backCommand = new Command("返回", Command.BACK, 3);

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

    public SettingsForm(ProviderProfile profile, CommandListener listener) {
        super("档案设置 - " + safe(profile.name));
        customProfile = ProviderPresets.CUSTOM.equals(profile.presetId);

        nameField = new TextField("档案名称", safe(profile.name), ProviderProfile.MAX_NAME_CHARS, TextField.ANY);
        endpointOverrideChoice = new ChoiceGroup("端点控制", ChoiceGroup.MULTIPLE,
                new String[] {"高级覆盖（用于兼容网关）"}, null);
        endpointOverrideChoice.setSelectedIndex(0, customProfile || profile.endpointOverride);
        endpointField = new TextField("Chat Completions 端点", safe(profile.endpoint),
                ProviderProfile.MAX_ENDPOINT_CHARS, TextField.URL);
        modelsEndpointField = new TextField("模型列表端点", safe(profile.modelsEndpoint),
                ProviderProfile.MAX_ENDPOINT_CHARS, TextField.URL);
        keyField = new TextField("API Key（可留空）", safe(profile.apiKey), ProviderProfile.MAX_API_KEY_CHARS,
                TextField.ANY | TextField.PASSWORD | TextField.SENSITIVE);
        modelField = new TextField("模型名称", safe(profile.model), ProviderProfile.MAX_MODEL_CHARS, TextField.ANY);
        systemField = new TextField("系统提示词", safe(profile.systemPrompt), ProviderProfile.MAX_SYSTEM_PROMPT_CHARS,
                TextField.ANY);

        streamChoice = new ChoiceGroup("响应方式", ChoiceGroup.MULTIPLE,
                new String[] {"启用流式显示"}, null);
        streamChoice.setSelectedIndex(0, profile.stream);

        thinkingChoice = new ChoiceGroup("思考模式", ChoiceGroup.EXCLUSIVE,
                THINKING_LABELS, null);
        int displayedThinkingMode = normalizeThinkingMode(profile.thinkingMode);
        if (ProviderPresets.isKimiAlwaysThinking(profile)
                && displayedThinkingMode == ProviderProfile.THINKING_OFF) {
            displayedThinkingMode = ProviderProfile.THINKING_ON;
        }
        thinkingChoice.setSelectedIndex(displayedThinkingMode, true);

        effortChoice = new ChoiceGroup("推理强度", ChoiceGroup.POPUP,
                EFFORT_LABELS, null);
        effortChoice.setSelectedIndex(find(EFFORT_LABELS, profile.reasoningEffort, 1), true);

        if (customProfile) {
            protocolChoice = new ChoiceGroup("思考参数格式", ChoiceGroup.POPUP,
                    PROTOCOL_LABELS, null);
            protocolChoice.setSelectedIndex(normalizeProtocol(profile.thinkingProtocol), true);
        } else {
            protocolChoice = null;
        }

        multimodalChoice = new ChoiceGroup("多模态", ChoiceGroup.MULTIPLE,
                new String[] {"允许选择和发送图片"}, null);
        multimodalChoice.setSelectedIndex(0, profile.multimodal);
        historyField = new TextField("携带最近消息数（2-24）",
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
            append(new StringItem("常开思考模型",
                    "该模型始终思考；选择“关闭”保存时会规范为“开启”。K3 使用思考强度，K2.7 Code 不发送开关字段。"));
        }
        append(new StringItem("高级覆盖",
                customProfile
                        ? "自定义档案始终采用上方端点。模型列表端点留空时会从聊天端点推导。"
                        : "未勾选时保存会恢复官方端点；勾选后可改为可信兼容网关。"));
        append(new StringItem("内存提示",
                "图片功能默认关闭。较少的历史消息可降低老设备的内存和网络负担。"));
        append(new StringItem("安全提示",
                "Java ME 没有安全密钥库。建议通过离线配置包导入短期网关令牌，并在传输后删除配置文件。"));

        addCommand(saveCommand);
        addCommand(modelsCommand);
        addCommand(backCommand);
        setCommandListener(listener);
    }

    /** Copies form values while deliberately preserving reasoningExpanded. */
    public void copyTo(ProviderProfile profile) {
        profile.name = nameField.getString().trim();
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




