package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.i18n.I18n;
import com.chihoko.j2mellm.i18n.TextId;
import com.chihoko.j2mellm.model.ResourceLimits;

import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;

/** Recommended device limits with an explicit custom unlock path. */
public final class ResourceLimitsForm extends Form {
    public final Command saveCommand = new Command(I18n.text(TextId.SAVE), Command.OK, 1);
    public final Command backCommand = new Command(I18n.text(TextId.BACK), Command.BACK, 2);

    private final ChoiceGroup modeChoice;
    private final TextField activeCharsField;
    private final TextField activeMessagesField;
    private final TextField messageCharsField;
    private final TextField reasoningCharsField;
    private final TextField requestCharsField;
    private final TextField savedMessagesField;
    private final TextField searchCharsField;
    private final TextField searchResultsField;
    private final ChoiceGroup imageModeChoice;
    private final TextField inputImageKbField;
    private final TextField imagePixelsField;
    private final TextField returnedImageKbField;

    public ResourceLimitsForm(ResourceLimits limits, CommandListener listener) {
        super(I18n.text(TextId.LIMITS));
        ResourceLimits value = limits == null ? ResourceLimits.recommended() : limits;
        value.normalize();
        modeChoice = new ChoiceGroup(I18n.text(TextId.RESOURCE_MODE), ChoiceGroup.POPUP,
                new String[] {
                    I18n.text(TextId.COMPATIBLE),
                    I18n.text(TextId.RECOMMENDED),
                    I18n.text(TextId.CUSTOM)
                }, null);
        modeChoice.setSelectedIndex(value.mode, true);
        activeCharsField = numeric(TextId.ACTIVE_CONTEXT_CHARS, value.activeConversationChars);
        activeMessagesField = numeric(TextId.MAX_ACTIVE_MESSAGES, value.activeMessages);
        messageCharsField = numeric(TextId.MESSAGE_CONTENT_CHARS, value.messageContentChars);
        reasoningCharsField = numeric(TextId.MESSAGE_REASONING_CHARS, value.messageReasoningChars);
        requestCharsField = numeric(TextId.REQUEST_CONTEXT_CHARS, value.requestContextChars);
        savedMessagesField = numeric(TextId.SAVED_MESSAGES, value.savedMessages);
        searchCharsField = numeric(TextId.SEARCH_CONTEXT_CHARS, value.searchContextChars);
        searchResultsField = numeric(TextId.SEARCH_RESULTS, value.searchResults);

        imageModeChoice = new ChoiceGroup(I18n.text(TextId.IMAGE_MODE), ChoiceGroup.POPUP,
                new String[] {
                    I18n.text(TextId.COMPATIBLE),
                    I18n.text(TextId.HIGH_PERFORMANCE),
                    I18n.text(TextId.CUSTOM)
                }, null);
        imageModeChoice.setSelectedIndex(value.imageMode, true);
        inputImageKbField = numeric(TextId.IMAGE_BYTES, value.maximumInputImageBytes / 1024);
        imagePixelsField = numeric(TextId.IMAGE_PIXELS, value.maximumImagePixels);
        returnedImageKbField = numeric(
                TextId.IMAGE_RESPONSE_BYTES, value.maximumReturnedImageBytes / 1024);

        append(modeChoice);
        append(activeCharsField);
        append(activeMessagesField);
        append(messageCharsField);
        append(reasoningCharsField);
        append(requestCharsField);
        append(savedMessagesField);
        append(searchCharsField);
        append(searchResultsField);
        append(new StringItem(I18n.text(TextId.UNLOCK_LIMITS),
                I18n.text(TextId.UNLOCK_WARNING)));
        append(imageModeChoice);
        append(inputImageKbField);
        append(imagePixelsField);
        append(returnedImageKbField);
        append(new StringItem(I18n.text(TextId.IMAGE_LIMITS),
                I18n.text(TextId.IMAGE_UNLOCK_WARNING) + " "
                + I18n.text(TextId.SIZE_KB_HELP)));
        addCommand(saveCommand);
        addCommand(backCommand);
        setCommandListener(listener);
    }

    public ResourceLimits read() {
        int mode = modeChoice.getSelectedIndex();
        ResourceLimits value;
        if (mode == ResourceLimits.MODE_COMPATIBLE) value = ResourceLimits.compatible();
        else if (mode == ResourceLimits.MODE_RECOMMENDED) value = ResourceLimits.recommended();
        else {
            value = new ResourceLimits();
            value.mode = ResourceLimits.MODE_CUSTOM;
            value.activeConversationChars = number(activeCharsField, 131072);
            value.activeMessages = number(activeMessagesField, 64);
            value.messageContentChars = number(messageCharsField, 49152);
            value.messageReasoningChars = number(reasoningCharsField, 16384);
            value.requestContextChars = number(requestCharsField, 96000);
            value.savedMessages = number(savedMessagesField, 64);
            value.searchContextChars = number(searchCharsField, 6000);
            value.searchResults = number(searchResultsField, 5);
        }

        int imageMode = imageModeChoice.getSelectedIndex();
        if (imageMode == ResourceLimits.IMAGE_COMPATIBLE) {
            ResourceLimits.applyCompatibleImages(value);
        } else if (imageMode == ResourceLimits.IMAGE_HIGH_PERFORMANCE) {
            ResourceLimits.applyHighPerformanceImages(value);
        } else {
            value.imageMode = ResourceLimits.IMAGE_CUSTOM;
            value.maximumInputImageBytes = kilobytes(inputImageKbField, 96);
            value.maximumImagePixels = number(imagePixelsField, 65536);
            value.maximumReturnedImageBytes = kilobytes(returnedImageKbField, 256);
        }
        value.normalize();
        return value;
    }

    private TextField numeric(int label, int value) {
        return new TextField(I18n.text(label), Integer.toString(value), 8, TextField.NUMERIC);
    }

    private int number(TextField field, int fallback) {
        try { return Integer.parseInt(field.getString()); }
        catch (Exception ignored) { return fallback; }
    }

    private int kilobytes(TextField field, int fallback) {
        int kb = number(field, fallback);
        if (kb > Integer.MAX_VALUE / 1024) return Integer.MAX_VALUE;
        return kb * 1024;
    }
}
