
package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ChatMessage;
import com.chihoko.j2mellm.model.ProviderPresets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/** A separate, recoverable conversation RMS store for each provider profile. */
public final class ProfileConversationStore {
    private static final int MAGIC = 0x4A324348;
    private static final int FORMAT_VERSION = 2;
    private static final int MAX_SAVED_MESSAGES = 24;
    private static final int MAX_RECORD_BYTES = 262144;
    private static final int PRIMARY_RECORD = 1;
    private static final int BACKUP_RECORD = 2;
    private final String profileId;
    private final String storeName;
    private boolean migratedLegacy;
    private boolean recoveredFromBackup;

    public ProfileConversationStore(String id) {
        profileId = sanitizeId(id);
        storeName = "J2CHAT_" + profileId;
    }

    public Vector load() {
        RecordStore store = null;
        boolean exists = false;
        boolean hasRecords = false;
        try {
            store = RecordStore.openRecordStore(storeName, false);
            exists = true;
            hasRecords = store.getNumRecords() > 0;
            Vector primary = decodeRecord(store, PRIMARY_RECORD);
            if (primary != null) return primary;
            Vector backup = decodeRecord(store, BACKUP_RECORD);
            if (backup != null) {
                recoveredFromBackup = true;
                repairPrimary(store, backup);
                return backup;
            }
        } catch (Exception ignored) {
        } finally {
            close(store);
        }
        if ((!exists || !hasRecords) && ProviderPresets.CUSTOM.equals(profileId)) {
            Vector legacy = new ConversationStore().load();
            if (legacy.size() > 0) {
                try {
                    save(legacy);
                    migratedLegacy = true;
                } catch (Exception ignored) {
                }
                return legacy;
            }
        }
        return new Vector();
    }

    public void save(Vector source) throws RecordStoreException, IOException {
        byte[] encoded = encode(source);
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(storeName, true);
            byte[] oldPrimary = validRecord(store, PRIMARY_RECORD);
            if (store.getNumRecords() == 0) {
                store.addRecord(encoded, 0, encoded.length);
                store.addRecord(encoded, 0, encoded.length);
                return;
            }
            if (oldPrimary != null) writeRecord(store, BACKUP_RECORD, oldPrimary);
            writeRecord(store, PRIMARY_RECORD, encoded);
            if (oldPrimary == null && validRecord(store, BACKUP_RECORD) == null) {
                writeRecord(store, BACKUP_RECORD, encoded);
            }
        } finally {
            close(store);
        }
    }

    public void clear() {
        try { RecordStore.deleteRecordStore(storeName); } catch (RecordStoreException ignored) { }
    }

    public boolean didMigrateLegacy() {
        return migratedLegacy;
    }

    public boolean didRecoverFromBackup() {
        return recoveredFromBackup;
    }

    public String getStoreName() {
        return storeName;
    }

    private byte[] encode(Vector source) throws IOException {
        Vector saved = new Vector();
        int i;
        for (i = source.size() - 1; i >= 0 && saved.size() < MAX_SAVED_MESSAGES; i--) {
            ChatMessage message = (ChatMessage) source.elementAt(i);
            if (!message.pending) saved.insertElementAt(message, 0);
        }

        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(payloadBytes);
        output.writeInt(MAGIC);
        output.writeInt(FORMAT_VERSION);
        output.writeUTF(profileId);
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
        byte[] payload = payloadBytes.toByteArray();
        output.close();

        ByteArrayOutputStream resultBytes = new ByteArrayOutputStream(payload.length + 4);
        resultBytes.write(payload);
        DataOutputStream result = new DataOutputStream(resultBytes);
        result.writeInt(ProfileCodec.crc32(payload, 0, payload.length));
        result.flush();
        byte[] encoded = resultBytes.toByteArray();
        result.close();
        return encoded;
    }

    private Vector decode(byte[] record) {
        Vector messages = new Vector();
        try {
            if (record == null || record.length < 20 || record.length > MAX_RECORD_BYTES) return null;
            int payloadLength = record.length - 4;
            if (readInt(record, payloadLength) != ProfileCodec.crc32(record, 0, payloadLength)) return null;
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(record, 0, payloadLength));
            if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) return null;
            if (!profileId.equals(input.readUTF())) return null;
            int count = input.readInt();
            if (count < 0 || count > MAX_SAVED_MESSAGES) return null;
            int i;
            for (i = 0; i < count; i++) {
                String role = input.readUTF();
                ChatMessage message = new ChatMessage(role, input.readUTF());
                message.appendReasoning(input.readUTF());
                message.error = input.readBoolean();
                message.setImageMetadata(input.readUTF(), input.readUTF());
                message.setImageSource(input.readUTF());
                if (ChatMessage.ROLE_USER.equals(role) || ChatMessage.ROLE_ASSISTANT.equals(role)) {
                    messages.addElement(message);
                }
            }
            return messages;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Vector decodeRecord(RecordStore store, int recordId) {
        try {
            return decode(store.getRecord(recordId));
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] validRecord(RecordStore store, int recordId) {
        try {
            byte[] record = store.getRecord(recordId);
            return ConversationRecordValidator.isValid(record, MAGIC, FORMAT_VERSION,
                    profileId, MAX_SAVED_MESSAGES, MAX_RECORD_BYTES) ? record : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void repairPrimary(RecordStore store, Vector recovered) {
        try {
            byte[] encoded = encode(recovered);
            store.setRecord(PRIMARY_RECORD, encoded, 0, encoded.length);
        } catch (Exception ignored) {
        }
    }

    private void writeRecord(RecordStore store, int recordId, byte[] record)
            throws RecordStoreException {
        try {
            store.setRecord(recordId, record, 0, record.length);
        } catch (RecordStoreException missing) {
            int id = store.addRecord(record, 0, record.length);
            if (id != recordId) throw missing;
        }
    }

    private String shorten(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static String sanitizeId(String value) {
        if (value == null || value.length() == 0) return ProviderPresets.CUSTOM;
        StringBuffer result = new StringBuffer();
        int i;
        for (i = 0; i < value.length() && result.length() < 20; i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-') result.append(c);
            else result.append('_');
        }
        return result.length() == 0 ? ProviderPresets.CUSTOM : result.toString();
    }

    private static int readInt(byte[] value, int offset) {
        return ((value[offset] & 0xff) << 24)
                | ((value[offset + 1] & 0xff) << 16)
                | ((value[offset + 2] & 0xff) << 8)
                | (value[offset + 3] & 0xff);
    }

    private void close(RecordStore store) {
        if (store != null) {
            try { store.closeRecordStore(); } catch (RecordStoreException ignored) { }
        }
    }
}

