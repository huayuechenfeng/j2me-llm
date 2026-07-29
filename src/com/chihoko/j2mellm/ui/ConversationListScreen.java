package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.i18n.I18n;
import com.chihoko.j2mellm.i18n.TextId;
import com.chihoko.j2mellm.model.ConversationMeta;
import com.chihoko.j2mellm.model.ConversationState;

import java.util.Vector;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.List;

/** Lists persisted conversations without loading every message body. */
public final class ConversationListScreen extends List {
    private static final String NEW_SENTINEL = "";

    public final Command newCommand = new Command(
            I18n.text(TextId.NEW_CHAT), Command.OK, 1);
    public final Command renameCommand = new Command(
            I18n.text(TextId.RENAME_CHAT), Command.SCREEN, 2);
    public final Command deleteCommand = new Command(
            I18n.text(TextId.DELETE_CHAT), Command.SCREEN, 3);
    public final Command backCommand = new Command(
            I18n.text(TextId.BACK), Command.BACK, 4);

    private final ConversationState state;
    private final Vector displayedIds = new Vector();

    public ConversationListScreen(ConversationState conversationState,
            CommandListener listener) {
        super(I18n.text(TextId.CHAT_LIST), List.IMPLICIT);
        state = conversationState;
        addCommand(newCommand);
        addCommand(renameCommand);
        addCommand(deleteCommand);
        addCommand(backCommand);
        setCommandListener(listener);
        refresh();
    }

    public void refresh() {
        String wanted = selectedConversationId();
        if (wanted == null) wanted = state.activeConversationId;
        deleteAll();
        displayedIds.removeAllElements();
        append("+ " + I18n.text(TextId.NEW_CHAT), null);
        displayedIds.addElement(NEW_SENTINEL);
        int selected = -1;
        int i;
        for (i = 0; i < state.conversations.size(); i++) {
            ConversationMeta meta = (ConversationMeta) state.conversations.elementAt(i);
            displayedIds.addElement(meta.id);
            append(label(meta), null);
            if (meta.id.equals(wanted)) selected = i + 1;
        }
        if (selected < 0) selected = state.conversations.size() == 0 ? 0 : 1;
        setSelectedIndex(selected, true);
    }

    public String selectedConversationId() {
        int selected = getSelectedIndex();
        if (selected < 0 || selected >= displayedIds.size()) return null;
        String id = (String) displayedIds.elementAt(selected);
        return id.length() == 0 ? null : id;
    }

    private String label(ConversationMeta meta) {
        StringBuffer text = new StringBuffer();
        text.append(meta.id.equals(state.activeConversationId) ? "* " : "  ");
        String title = meta.title == null ? "" : meta.title.trim();
        text.append(title.length() == 0 ? I18n.text(TextId.NEW_CHAT_TITLE) : title);
        String preview = meta.preview == null ? "" : meta.preview.trim();
        if (preview.length() > 0 && !preview.equals(title)) {
            text.append(" - ").append(preview);
        }
        return text.toString();
    }
}
