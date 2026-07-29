package com.chihoko.j2mellm.model;

import java.util.Vector;

/** Versioned conversation index loaded from RMS. */
public final class ConversationState {
    public final Vector conversations = new Vector();
    public String activeConversationId = "";

    /* Load diagnostics; deliberately not persisted. */
    public boolean migratedThisLoad;
    public boolean recoveredFromBackup;
    public boolean storageCorrupt;

    public ConversationMeta find(String conversationId) {
        if (conversationId == null) return null;
        int i;
        for (i = 0; i < conversations.size(); i++) {
            ConversationMeta value = (ConversationMeta) conversations.elementAt(i);
            if (conversationId.equals(value.id)) return value;
        }
        return null;
    }

    public ConversationMeta getActive() {
        ConversationMeta value = find(activeConversationId);
        if (value != null) return value;
        if (conversations.size() == 0) return null;
        value = (ConversationMeta) conversations.elementAt(0);
        activeConversationId = value.id;
        return value;
    }

    public void add(ConversationMeta value) {
        if (value == null || find(value.id) != null) return;
        conversations.addElement(value);
        activeConversationId = value.id;
    }

    public boolean remove(String conversationId) {
        ConversationMeta value = find(conversationId);
        if (value == null) return false;
        conversations.removeElement(value);
        if (conversationId.equals(activeConversationId)) {
            activeConversationId = conversations.size() == 0 ? ""
                    : ((ConversationMeta) conversations.elementAt(0)).id;
        }
        return true;
    }
}
