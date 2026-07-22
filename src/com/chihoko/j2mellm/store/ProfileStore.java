

package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ProfileState;
import com.chihoko.j2mellm.model.ProviderPresets;
import com.chihoko.j2mellm.model.ProviderProfile;

import java.io.IOException;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/** Dual-copy RMS storage for all v0.2 provider profiles. */
public final class ProfileStore {
    public static final String STORE_NAME = "J2MELLM_PROFILES";
    public static final String LEGACY_STORE_NAME = "J2MELLM_CFG";
    private static final int PRIMARY_RECORD = 1;
    private static final int BACKUP_RECORD = 2;

    public ProfileState load() {
        RecordStore store = null;
        boolean newStoreExists = false;
        boolean newStoreHasRecords = false;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, false);
            newStoreExists = true;
            newStoreHasRecords = store.getNumRecords() > 0;
            ProfileState primary = decodeRecord(store, PRIMARY_RECORD);
            if (primary != null) return primary;
            ProfileState backup = decodeRecord(store, BACKUP_RECORD);
            if (backup != null) {
                backup.recoveredFromBackup = true;
                repairPrimary(store, backup);
                return backup;
            }
        } catch (RecordStoreException ignored) {
        } finally {
            close(store);
        }

        ProfileState state = ProviderPresets.createDefaultState();
        if (newStoreExists && newStoreHasRecords) {
            state.storageCorrupt = true;
            return state;
        }

        ProviderProfile legacy = loadLegacy();
        if (legacy != null) {
            state.replace(legacy);
            state.activeProfileId = ProviderPresets.CUSTOM;
            state.legacyMigrated = true;
            state.migratedThisLoad = true;
        }
        try {
            save(state);
        } catch (Exception ignored) {
        }
        return state;
    }

    public void save(ProfileState state) throws RecordStoreException, IOException {
        byte[] encoded = ProfileCodec.encode(state);
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

    private ProviderProfile loadLegacy() {
        RecordStore legacy = null;
        try {
            legacy = RecordStore.openRecordStore(LEGACY_STORE_NAME, false);
            return LegacyConfigCodec.decode(legacy.getRecord(1));
        } catch (Exception ignored) {
            return null;
        } finally {
            close(legacy);
        }
    }

    private ProfileState decodeRecord(RecordStore store, int recordId) {
        try {
            return ProfileCodec.decode(store.getRecord(recordId));
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] validRecord(RecordStore store, int recordId) {
        try {
            byte[] record = store.getRecord(recordId);
            return ProfileCodec.isValidRecord(record) ? record : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void repairPrimary(RecordStore store, ProfileState recovered) {
        try {
            byte[] encoded = ProfileCodec.encode(recovered);
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

    private void close(RecordStore store) {
        if (store != null) {
            try { store.closeRecordStore(); } catch (RecordStoreException ignored) { }
        }
    }
}


