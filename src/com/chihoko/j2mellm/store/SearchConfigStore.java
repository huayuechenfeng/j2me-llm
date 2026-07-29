package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.SearchConfig;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/** RMS storage for the global search provider configuration. */
public final class SearchConfigStore {
    private static final String STORE_NAME = "J2MELLM_SEARCH";

    public SearchConfig load() {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, false);
            return SearchConfigCodec.decode(store.getRecord(1));
        } catch (Exception ignored) {
            return new SearchConfig();
        } finally {
            close(store);
        }
    }

    public void save(SearchConfig value) throws Exception {
        byte[] encoded = SearchConfigCodec.encode(value);
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
