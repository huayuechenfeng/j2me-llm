package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ChatMessage;
import com.chihoko.j2mellm.model.ConversationMeta;
import com.chihoko.j2mellm.util.Crc32;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/** Per-conversation RMS store with recoverable indexes and independent message records. */
public final class ConversationStoreV3 {
    private static final int INDEX_MAGIC = 0x4A324358;
    private static final int INDEX_VERSION = 1;
    private static final int PRIMARY_INDEX = 1;
    private static final int BACKUP_INDEX = 2;
    private static final int MAX_INDEX_BYTES = 4096;
    private static final int MAX_MESSAGES = 256;

    private final String conversationId;
    private final String profileId;
    private final String storeName;
    private boolean recoveredFromBackup;

    public ConversationStoreV3(ConversationMeta meta) {
        if (meta == null) throw new IllegalArgumentException("Missing conversation");
        conversationId = meta.id;
        profileId = meta.profileId == null ? "" : meta.profileId;
        storeName = "J2C_" + sanitizeId(conversationId);
    }

    public Vector load() {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(storeName, false);
            Index primary = decodeIndex(record(store, PRIMARY_INDEX));
            Vector messages = loadMessages(store, primary);
            if (messages != null) return messages;
            Index backup = decodeIndex(record(store, BACKUP_INDEX));
            messages = loadMessages(store, backup);
            if (messages != null) {
                recoveredFromBackup = true;
                repairPrimary(store, backup);
                return messages;
            }
        } catch (Exception ignored) {
        } finally {
            close(store);
        }
        return new Vector();
    }

    public void save(Vector source, int maximumSavedMessages)
            throws IOException, RecordStoreException {
        if (source == null) return;
        int maximum = maximumSavedMessages;
        if (maximum < 1) maximum = 1;
        if (maximum > MAX_MESSAGES) maximum = MAX_MESSAGES;
        Vector selected = new Vector();
        int i;
        for (i = source.size() - 1; i >= 0 && selected.size() < maximum; i--) {
            ChatMessage message = (ChatMessage) source.elementAt(i);
            if (!message.pending) selected.insertElementAt(message, 0);
        }

        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(storeName, true);
            boolean fresh = store.getNumRecords() == 0;
            if (!fresh && (record(store, PRIMARY_INDEX) == null
                    || record(store, BACKUP_INDEX) == null)) {
                close(store);
                store = null;
                try { RecordStore.deleteRecordStore(storeName); }
                catch (RecordStoreException ignored) { }
                store = RecordStore.openRecordStore(storeName, true);
                fresh = true;
            }
            if (fresh) {
                byte[] empty = encodeIndex(new Index());
                store.addRecord(empty, 0, empty.length);
                store.addRecord(empty, 0, empty.length);
            }
            byte[] oldPrimary = record(store, PRIMARY_INDEX);
            Index oldIndex = decodeIndex(oldPrimary);

            Index next = new Index();
            for (i = 0; i < selected.size(); i++) {
                byte[] encoded = ConversationMessageCodec.encode(
                        (ChatMessage) selected.elementAt(i));
                int recordId = store.addRecord(encoded, 0, encoded.length);
                next.recordIds.addElement(new Integer(recordId));
            }
            byte[] nextBytes = encodeIndex(next);
            if (fresh || oldIndex == null) {
                writeRecord(store, BACKUP_INDEX, nextBytes);
            } else {
                writeRecord(store, BACKUP_INDEX, oldPrimary);
            }
            writeRecord(store, PRIMARY_INDEX, nextBytes);
            cleanupOrphans(store, next, fresh || oldIndex == null ? next : oldIndex);
        } finally {
            close(store);
        }
    }

    public void clear() {
        try { RecordStore.deleteRecordStore(storeName); } catch (RecordStoreException ignored) { }
    }

    public boolean didRecoverFromBackup() {
        return recoveredFromBackup;
    }

    public String getStoreName() {
        return storeName;
    }

    private Vector loadMessages(RecordStore store, Index index) {
        if (store == null || index == null) return null;
        Vector messages = new Vector();
        int i;
        try {
            for (i = 0; i < index.recordIds.size(); i++) {
                int recordId = ((Integer) index.recordIds.elementAt(i)).intValue();
                byte[] encoded = store.getRecord(recordId);
                messages.addElement(ConversationMessageCodec.decode(encoded));
            }
            return messages;
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] encodeIndex(Index index) throws IOException {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(payloadBytes);
        output.writeInt(INDEX_MAGIC);
        output.writeInt(INDEX_VERSION);
        output.writeUTF(conversationId);
        output.writeUTF(profileId);
        output.writeInt(index.recordIds.size());
        int i;
        for (i = 0; i < index.recordIds.size(); i++) {
            output.writeInt(((Integer) index.recordIds.elementAt(i)).intValue());
        }
        output.flush();
        byte[] payload = payloadBytes.toByteArray();
        output.close();
        ByteArrayOutputStream recordBytes = new ByteArrayOutputStream(payload.length + 4);
        recordBytes.write(payload);
        DataOutputStream result = new DataOutputStream(recordBytes);
        result.writeInt(Crc32.compute(payload, 0, payload.length));
        result.flush();
        byte[] encoded = recordBytes.toByteArray();
        result.close();
        if (encoded.length > MAX_INDEX_BYTES) throw new IOException("Conversation index is too large");
        return encoded;
    }

    private Index decodeIndex(byte[] record) {
        try {
            if (record == null || record.length < 20 || record.length > MAX_INDEX_BYTES) return null;
            int payloadLength = record.length - 4;
            if (readInt(record, payloadLength) != Crc32.compute(record, 0, payloadLength)) return null;
            DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(record, 0, payloadLength));
            if (input.readInt() != INDEX_MAGIC || input.readInt() != INDEX_VERSION) return null;
            if (!conversationId.equals(input.readUTF())) return null;
            input.readUTF(); // The profile is informational and may change for an existing chat.
            int count = input.readInt();
            if (count < 0 || count > MAX_MESSAGES || input.available() < count * 4) return null;
            Index index = new Index();
            int i;
            for (i = 0; i < count; i++) {
                int recordId = input.readInt();
                if (recordId <= BACKUP_INDEX || contains(index.recordIds, recordId)) return null;
                index.recordIds.addElement(new Integer(recordId));
            }
            return index;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void cleanupOrphans(RecordStore store, Index primary, Index backup) {
        RecordEnumeration records = null;
        try {
            Vector delete = new Vector();
            records = store.enumerateRecords(null, null, false);
            while (records.hasNextElement()) {
                int recordId = records.nextRecordId();
                if (recordId > BACKUP_INDEX && !contains(primary.recordIds, recordId)
                        && !contains(backup.recordIds, recordId)) {
                    delete.addElement(new Integer(recordId));
                }
            }
            int i;
            for (i = 0; i < delete.size(); i++) {
                try {
                    store.deleteRecord(((Integer) delete.elementAt(i)).intValue());
                } catch (RecordStoreException ignored) {
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (records != null) records.destroy();
        }
    }

    private void repairPrimary(RecordStore store, Index index) {
        try {
            byte[] encoded = encodeIndex(index);
            writeRecord(store, PRIMARY_INDEX, encoded);
        } catch (Exception ignored) {
        }
    }

    private byte[] record(RecordStore store, int recordId) {
        try {
            return store.getRecord(recordId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeRecord(RecordStore store, int recordId, byte[] value)
            throws RecordStoreException {
        try {
            store.setRecord(recordId, value, 0, value.length);
        } catch (RecordStoreException missing) {
            int id = store.addRecord(value, 0, value.length);
            if (id != recordId) throw missing;
        }
    }

    private boolean contains(Vector values, int target) {
        int i;
        for (i = 0; i < values.size(); i++) {
            if (((Integer) values.elementAt(i)).intValue() == target) return true;
        }
        return false;
    }

    private static String sanitizeId(String value) {
        StringBuffer result = new StringBuffer();
        int i;
        for (i = 0; value != null && i < value.length() && result.length() < 24; i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-') result.append(c);
            else result.append('_');
        }
        return result.length() == 0 ? "default" : result.toString();
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

    private static final class Index {
        final Vector recordIds = new Vector();
    }
}
