package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ResourceLimits;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/** Independent RMS preference store for device resource limits. */
public final class ResourceLimitsStore {
    private static final String STORE_NAME = "J2MELLM_LIMITS";

    public ResourceLimits load() {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, false);
            return ResourceLimitsCodec.decode(store.getRecord(1));
        } catch (Exception ignored) {
            return ResourceLimits.recommended();
        } finally {
            close(store);
        }
    }

    public void save(ResourceLimits limits) throws Exception {
        byte[] encoded = ResourceLimitsCodec.encode(limits);
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, true);
            if (store.getNumRecords() == 0) store.addRecord(encoded, 0, encoded.length);
            else store.setRecord(1, encoded, 0, encoded.length);
        } finally {
            close(store);
        }
    }

    private void close(RecordStore store) {
        if (store != null) {
            try { store.closeRecordStore(); } catch (RecordStoreException ignored) { }
        }
    }
}
