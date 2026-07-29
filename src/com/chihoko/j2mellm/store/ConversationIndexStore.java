package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ConversationState;

import java.io.IOException;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/** Recoverable RMS storage for the v0.4 conversation index. */
public final class ConversationIndexStore {
    public static final String STORE_NAME = "J2MELLM_CHATS";
    private static final int PRIMARY_RECORD = 1;
    private static final int BACKUP_RECORD = 2;

    public ConversationState load() {
        RecordStore store = null;
        boolean exists = false;
        boolean hasRecords = false;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, false);
            exists = true;
            hasRecords = store.getNumRecords() > 0;
            ConversationState primary = decodeRecord(store, PRIMARY_RECORD);
            if (primary != null) return primary;
            ConversationState backup = decodeRecord(store, BACKUP_RECORD);
            if (backup != null) {
                backup.recoveredFromBackup = true;
                repairPrimary(store, backup);
                return backup;
            }
        } catch (Exception ignored) {
        } finally {
            close(store);
        }
        ConversationState empty = new ConversationState();
        empty.storageCorrupt = exists && hasRecords;
        return empty;
    }

    public void save(ConversationState state) throws IOException, RecordStoreException {
        byte[] encoded = ConversationIndexCodec.encode(state);
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, true);
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

    private ConversationState decodeRecord(RecordStore store, int recordId) {
        try {
            return ConversationIndexCodec.decode(store.getRecord(recordId));
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] validRecord(RecordStore store, int recordId) {
        try {
            byte[] value = store.getRecord(recordId);
            return ConversationIndexCodec.isValidRecord(value) ? value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void repairPrimary(RecordStore store, ConversationState state) {
        try {
            byte[] encoded = ConversationIndexCodec.encode(state);
            writeRecord(store, PRIMARY_RECORD, encoded);
        } catch (Exception ignored) {
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

    private void close(RecordStore store) {
        if (store != null) {
            try { store.closeRecordStore(); } catch (RecordStoreException ignored) { }
        }
    }
}
