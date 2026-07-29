package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.i18n.I18n;
import com.chihoko.j2mellm.i18n.TextId;
import com.chihoko.j2mellm.model.ChatMessage;

import java.util.Vector;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.List;

/** Keypad-friendly message selection for edit and regenerate actions. */
public final class MessageListScreen extends List {
    public final Command editCommand = new Command(
            I18n.text(TextId.EDIT_AND_RESEND), Command.OK, 1);
    public final Command regenerateCommand = new Command(
            I18n.text(TextId.REGENERATE), Command.SCREEN, 2);
    public final Command backCommand = new Command(
            I18n.text(TextId.BACK), Command.BACK, 3);

    private final Vector messages;

    public MessageListScreen(Vector conversationMessages, CommandListener listener) {
        super(I18n.text(TextId.MESSAGE_LIST), List.IMPLICIT);
        messages = conversationMessages;
        addCommand(editCommand);
        addCommand(regenerateCommand);
        addCommand(backCommand);
        setCommandListener(listener);
        refresh();
    }

    public void refresh() {
        int wanted = getSelectedIndex();
        deleteAll();
        int i;
        for (i = 0; i < messages.size(); i++) {
            ChatMessage message = (ChatMessage) messages.elementAt(i);
            String prefix = ChatMessage.ROLE_USER.equals(message.role)
                    ? I18n.text(TextId.USER_MESSAGE_PREFIX)
                    : I18n.text(TextId.ASSISTANT_MESSAGE_PREFIX);
            String value = singleLine(message.getContent());
            if (value.length() > 120) value = value.substring(0, 117) + "...";
            append(prefix + value, null);
        }
        if (messages.size() == 0) append(I18n.text(TextId.NO_MESSAGES), null);
        else {
            if (wanted < 0 || wanted >= messages.size()) wanted = messages.size() - 1;
            setSelectedIndex(wanted, true);
        }
    }

    public int selectedMessageIndex() {
        int index = getSelectedIndex();
        return index >= 0 && index < messages.size() ? index : -1;
    }

    public ChatMessage selectedMessage() {
        int index = selectedMessageIndex();
        return index < 0 ? null : (ChatMessage) messages.elementAt(index);
    }

    private String singleLine(String value) {
        if (value == null) return "";
        StringBuffer result = new StringBuffer();
        int i;
        for (i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            result.append(c == '\r' || c == '\n' ? ' ' : c);
        }
        return result.toString().trim();
    }
}
