package com.chihoko.j2mellm.store;

import com.chihoko.j2mellm.model.ProviderConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

public final class ConfigStore {
    private static final String STORE_NAME = "J2MELLM_CFG";
    private static final int FORMAT_VERSION = 1;

    public ProviderConfig load() {
        ProviderConfig config = new ProviderConfig();
        RecordStore store = null;
        DataInputStream input = null;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, false);
            if (store.getNumRecords() == 0) return config;
            input = new DataInputStream(new ByteArrayInputStream(store.getRecord(1)));
            int version = input.readInt();
            if (version != FORMAT_VERSION) return config;
            config.name = input.readUTF();
            config.endpoint = input.readUTF();
            config.apiKey = input.readUTF();
            config.model = input.readUTF();
            config.systemPrompt = input.readUTF();
            config.stream = input.readBoolean();
            config.historyMessages = input.readInt();
        } catch (Exception ignored) {
        } finally {
            close(input);
            close(store);
        }
        return config;
    }

    public void save(ProviderConfig config) throws RecordStoreException, IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(FORMAT_VERSION);
        output.writeUTF(safe(config.name));
        output.writeUTF(safe(config.endpoint));
        output.writeUTF(safe(config.apiKey));
        output.writeUTF(safe(config.model));
        output.writeUTF(safe(config.systemPrompt));
        output.writeBoolean(config.stream);
        output.writeInt(config.historyMessages);
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void close(DataInputStream input) {
        if (input != null) {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void close(RecordStore store) {
        if (store != null) {
            try {
                store.closeRecordStore();
            } catch (RecordStoreException ignored) {
            }
        }
    }
}

