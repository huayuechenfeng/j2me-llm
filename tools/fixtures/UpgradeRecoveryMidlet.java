


package com.chihoko.j2mellm.tests;

import com.chihoko.j2mellm.model.ChatMessage;
import com.chihoko.j2mellm.model.ProfileState;
import com.chihoko.j2mellm.model.ProviderPresets;
import com.chihoko.j2mellm.model.ProviderProfile;
import com.chihoko.j2mellm.store.ProfileConversationStore;
import com.chihoko.j2mellm.store.ProfileStore;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Vector;

import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/** Runs real RecordStore migration and primary/backup recovery in MicroEmulator. */
public final class UpgradeRecoveryMidlet extends MIDlet {
    protected void startApp() {
        try {
            resetStores();
            writeLegacyConfig();
            writeLegacyConversation();
            RecordStore emptyV2 = RecordStore.openRecordStore(ProfileStore.STORE_NAME, true);
            emptyV2.closeRecordStore();
            RecordStore emptyChatV2 = RecordStore.openRecordStore("J2CHAT_custom", true);
            emptyChatV2.closeRecordStore();

            ProfileStore profiles = new ProfileStore();
            ProfileState migrated = profiles.load();
            ProviderProfile custom = migrated.find(ProviderPresets.CUSTOM);
            require(migrated.legacyMigrated, "legacy profile marker");
            require(migrated.migratedThisLoad, "one-shot migration diagnostic");
            require(ProviderPresets.CUSTOM.equals(migrated.activeProfileId), "legacy active profile");
            require("old-key".equals(custom.apiKey), "legacy key");
            require("old-model".equals(custom.model), "legacy model");
            require(storeExists(ProfileStore.LEGACY_STORE_NAME), "legacy config retained");

            ProfileConversationStore chats = new ProfileConversationStore(ProviderPresets.CUSTOM);
            Vector legacyMessages = chats.load();
            require(chats.didMigrateLegacy(), "legacy chat marker");
            require(legacyMessages.size() == 1, "legacy chat count");
            require("old question".equals(((ChatMessage) legacyMessages.elementAt(0)).getContent()),
                    "legacy chat content");
            require(storeExists("J2MELLM_CHAT"), "legacy chat retained");

            custom.model = "new-model";
            profiles.save(migrated);
            corruptPrimary(ProfileStore.STORE_NAME);
            ProfileState recovered = profiles.load();
            require(recovered.recoveredFromBackup, "profile backup recovery marker");
            require(!recovered.migratedThisLoad, "migration diagnostic not repeated");
            require("old-model".equals(recovered.find(ProviderPresets.CUSTOM).model),
                    "profile backup value");

            legacyMessages.addElement(new ChatMessage(ChatMessage.ROLE_ASSISTANT, "new answer"));
            chats.save(legacyMessages);
            corruptPrimary(chats.getStoreName());
            ProfileConversationStore recoveredChats =
                    new ProfileConversationStore(ProviderPresets.CUSTOM);
            Vector recoveredMessages = recoveredChats.load();
            require(recoveredChats.didRecoverFromBackup(), "chat backup recovery marker");
            require(recoveredMessages.size() == 1, "chat backup value");

            System.out.println("UPGRADE_RECOVERY_MIDLET_PASSED");
        } catch (Throwable failure) {
            System.out.println("UPGRADE_RECOVERY_MIDLET_FAILED: " + failure.toString());
            failure.printStackTrace();
        }
        notifyDestroyed();
    }

    protected void pauseApp() { }

    protected void destroyApp(boolean unconditional) { }

    private void writeLegacyConfig() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(1);
        output.writeUTF("Old profile");
        output.writeUTF("https://legacy.example/v1/chat/completions");
        output.writeUTF("old-key");
        output.writeUTF("old-model");
        output.writeUTF("old system");
        output.writeBoolean(true);
        output.writeInt(7);
        output.flush();
        writeRecord("J2MELLM_CFG", bytes.toByteArray());
        output.close();
    }

    private void writeLegacyConversation() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(1);
        output.writeInt(1);
        output.writeUTF(ChatMessage.ROLE_USER);
        output.writeUTF("old question");
        output.writeUTF("");
        output.writeBoolean(false);
        output.writeUTF("");
        output.writeUTF("");
        output.writeUTF("");
        output.flush();
        writeRecord("J2MELLM_CHAT", bytes.toByteArray());
        output.close();
    }

    private void writeRecord(String name, byte[] data) throws Exception {
        RecordStore store = RecordStore.openRecordStore(name, true);
        try {
            if (store.getNumRecords() == 0) store.addRecord(data, 0, data.length);
            else store.setRecord(1, data, 0, data.length);
        } finally {
            store.closeRecordStore();
        }
    }

    private void corruptPrimary(String name) throws Exception {
        RecordStore store = RecordStore.openRecordStore(name, false);
        try {
            byte[] corrupt = new byte[] {1, 2, 3, 4, 5};
            store.setRecord(1, corrupt, 0, corrupt.length);
        } finally {
            store.closeRecordStore();
        }
    }

    private boolean storeExists(String name) {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(name, false);
            return true;
        } catch (Exception missing) {
            return false;
        } finally {
            if (store != null) try { store.closeRecordStore(); }
            catch (RecordStoreException ignored) { }
        }
    }

    private void resetStores() {
        String[] stores = new String[] {
            ProfileStore.STORE_NAME, ProfileStore.LEGACY_STORE_NAME,
            "J2MELLM_CHAT", "J2CHAT_custom"
        };
        int i;
        for (i = 0; i < stores.length; i++) {
            try { RecordStore.deleteRecordStore(stores[i]); }
            catch (RecordStoreException ignored) { }
        }
    }

    private void require(boolean value, String label) {
        if (!value) throw new RuntimeException("failed: " + label);
    }
}



