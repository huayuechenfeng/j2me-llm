package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.i18n.I18n;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/** Stores one global UI-language preference without changing profile records. */
public final class LanguageStore {
    public static final String STORE_NAME = "J2MELLM_UI_PREFS";

    public int load() {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, false);
            byte[] record = store.getRecord(1);
            if (record != null && record.length == 1) {
                int value = record[0] & 0xff;
                if (value == I18n.ZH || value == I18n.EN) return value;
            }
        } catch (Exception ignored) {
        } finally {
            close(store);
        }
        return I18n.AUTO;
    }

    public void save(int preference) throws RecordStoreException {
        int normalized = preference == I18n.ZH || preference == I18n.EN
                ? preference : I18n.AUTO;
        byte[] record = new byte[] {(byte) normalized};
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, true);
            if (store.getNumRecords() == 0) store.addRecord(record, 0, record.length);
            else store.setRecord(1, record, 0, record.length);
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
