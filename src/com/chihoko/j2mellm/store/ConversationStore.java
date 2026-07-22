package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ChatMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

public final class ConversationStore {
    private static final String STORE_NAME = "J2MELLM_CHAT";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_SAVED_MESSAGES = 12;

    public Vector load() {
        Vector messages = new Vector();
        RecordStore store = null;
        DataInputStream input = null;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, false);
            if (store.getNumRecords() == 0) return messages;
            input = new DataInputStream(new ByteArrayInputStream(store.getRecord(1)));
            if (input.readInt() != FORMAT_VERSION) return messages;
            int count = input.readInt();
            int i;
            for (i = 0; i < count && i < MAX_SAVED_MESSAGES; i++) {
                String role = input.readUTF();
                ChatMessage message = new ChatMessage(role, input.readUTF());
                message.appendReasoning(input.readUTF());
                message.error = input.readBoolean();
                String imageName = input.readUTF();
                String imageMime = input.readUTF();
                String imageSource = input.readUTF();
                message.setImageMetadata(imageName, imageMime);
                message.setImageSource(imageSource);
                if (ChatMessage.ROLE_USER.equals(role) || ChatMessage.ROLE_ASSISTANT.equals(role)) {
                    messages.addElement(message);
                }
            }
        } catch (Exception ignored) {
            messages.removeAllElements();
        } finally {
            close(input);
            close(store);
        }
        return messages;
    }

    public void save(Vector source) throws RecordStoreException, IOException {
        Vector saved = new Vector();
        int start = source.size() - MAX_SAVED_MESSAGES;
        if (start < 0) start = 0;
        int i;
        for (i = start; i < source.size(); i++) {
            ChatMessage message = (ChatMessage) source.elementAt(i);
            if (!message.pending) saved.addElement(message);
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(FORMAT_VERSION);
        output.writeInt(saved.size());
        for (i = 0; i < saved.size(); i++) {
            ChatMessage message = (ChatMessage) saved.elementAt(i);
            output.writeUTF(shorten(message.role, 16));
            output.writeUTF(shorten(message.getContent(), 5000));
            output.writeUTF(shorten(message.getReasoning(), 1500));
            output.writeBoolean(message.error);
            output.writeUTF(shorten(message.getImageName(), 128));
            output.writeUTF(shorten(message.getImageMime(), 48));
            String imageSource = message.getImageSource();
            if (imageSource.startsWith("data:")) imageSource = "";
            output.writeUTF(shorten(imageSource, 2048));
        }
        output.flush();
        byte[] record = bytes.toByteArray();
        output.close();

        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, true);
            if (store.getNumRecords() == 0) {
                store.addRecord(record, 0, record.length);
            } else {
                store.setRecord(1, record, 0, record.length);
            }
        } finally {
            close(store);
        }
    }

    public void clear() throws RecordStoreException {
        try {
            RecordStore.deleteRecordStore(STORE_NAME);
        } catch (RecordStoreException ignored) {
        }
    }

    private String shorten(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private void close(DataInputStream input) {
        if (input != null) {
            try { input.close(); } catch (IOException ignored) { }
        }
    }

    private void close(RecordStore store) {
        if (store != null) {
            try { store.closeRecordStore(); } catch (RecordStoreException ignored) { }
        }
    }
}
