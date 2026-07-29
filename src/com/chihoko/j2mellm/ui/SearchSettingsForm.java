package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.i18n.I18n;
import com.chihoko.j2mellm.i18n.TextId;
import com.chihoko.j2mellm.model.SearchConfig;
import com.chihoko.j2mellm.model.SearchPresets;

import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;

/** Configures keyless and commercial web-search providers. */
public final class SearchSettingsForm extends Form implements ItemStateListener {
    private static final String[] IDS = {
        SearchConfig.FREE_COMPOSITE,
        SearchConfig.PUBLIC_SEARXNG,
        SearchConfig.BRAVE,
        SearchConfig.TAVILY,
        SearchConfig.EXA,
        SearchConfig.CUSTOM
    };

    public final Command saveCommand = new Command(I18n.text(TextId.SAVE), Command.OK, 1);
    public final Command testCommand = new Command(
            I18n.text(TextId.TEST_SEARCH), Command.SCREEN, 2);
    public final Command backCommand = new Command(I18n.text(TextId.BACK), Command.BACK, 3);

    private final ChoiceGroup enabledChoice;
    private final ChoiceGroup presetChoice;
    private final TextField endpointField;
    private final TextField keyField;
    private final TextField resultsField;
    private final String originalPreset;
    private final String originalEndpoint;
    private String displayedPreset;

    public SearchSettingsForm(SearchConfig config, CommandListener listener) {
        super(I18n.text(TextId.SEARCH_SETTINGS));
        SearchConfig value = config == null ? new SearchConfig() : config;
        value.normalize();
        originalPreset = value.presetId;
        originalEndpoint = value.endpoint;
        displayedPreset = value.presetId;

        enabledChoice = new ChoiceGroup(I18n.text(TextId.WEB_SEARCH),
                ChoiceGroup.MULTIPLE,
                new String[] {I18n.text(TextId.SEARCH_ENABLED)}, null);
        enabledChoice.setSelectedIndex(0, value.enabled);
        String[] labels = {
            I18n.text(TextId.FREE_COMPOSITE),
            I18n.text(TextId.PUBLIC_SEARXNG),
            I18n.text(TextId.BRAVE),
            I18n.text(TextId.TAVILY),
            I18n.text(TextId.EXA),
            I18n.text(TextId.CUSTOM_SEARCH)
        };
        presetChoice = new ChoiceGroup(I18n.text(TextId.SEARCH_PROVIDER),
                ChoiceGroup.POPUP, labels, null);
        presetChoice.setSelectedIndex(find(value.presetId), true);
        String endpoint = value.endpoint.length() == 0
                ? SearchPresets.defaultEndpoint(value.presetId) : value.endpoint;
        endpointField = new TextField(I18n.text(TextId.SEARCH_ENDPOINT),
                endpoint, SearchConfig.MAX_ENDPOINT_CHARS, TextField.URL);
        keyField = new TextField(I18n.text(TextId.SEARCH_API_KEY), value.apiKey,
                SearchConfig.MAX_API_KEY_CHARS,
                TextField.ANY | TextField.PASSWORD | TextField.SENSITIVE);
        resultsField = new TextField(I18n.text(TextId.SEARCH_RESULTS),
                Integer.toString(value.maximumResults), 2, TextField.NUMERIC);

        append(enabledChoice);
        append(presetChoice);
        append(endpointField);
        append(keyField);
        append(resultsField);
        append(new StringItem(I18n.text(TextId.FREE_COMPOSITE),
                I18n.text(TextId.SEARCH_FREE_NOTICE)));
        append(new StringItem(I18n.text(TextId.SECURITY_HINT),
                I18n.text(TextId.SEARCH_UNTRUSTED_NOTICE)));
        addCommand(saveCommand);
        addCommand(testCommand);
        addCommand(backCommand);
        setItemStateListener(this);
        setCommandListener(listener);
    }

    public void itemStateChanged(Item item) {
        if (item != presetChoice) return;
        String preset = IDS[presetChoice.getSelectedIndex()];
        String endpoint = endpointField.getString().trim();
        String displayedDefault = SearchPresets.defaultEndpoint(displayedPreset);
        if (endpoint.length() == 0 || endpoint.equals(displayedDefault)) {
            endpointField.setString(SearchPresets.defaultEndpoint(preset));
        }
        displayedPreset = preset;
    }

    public void copyTo(SearchConfig target) {
        String preset = IDS[presetChoice.getSelectedIndex()];
        String endpoint = endpointField.getString().trim();
        String oldDefault = SearchPresets.defaultEndpoint(originalPreset);
        if (!preset.equals(originalPreset)
                && (endpoint.length() == 0 || endpoint.equals(originalEndpoint)
                || endpoint.equals(oldDefault))) {
            endpoint = SearchPresets.defaultEndpoint(preset);
        }
        target.enabled = enabledChoice.isSelected(0);
        target.presetId = preset;
        target.endpoint = endpoint;
        target.apiKey = keyField.getString().trim();
        try {
            target.maximumResults = Integer.parseInt(resultsField.getString());
        } catch (NumberFormatException ignored) {
            target.maximumResults = 5;
        }
        target.normalize();
    }

    private int find(String id) {
        int i;
        for (i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return IDS.length - 1;
    }
}
