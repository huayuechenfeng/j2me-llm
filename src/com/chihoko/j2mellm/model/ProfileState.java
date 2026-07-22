
package com.chihoko.j2mellm.model;

import java.util.Vector;

/** The versioned multi-profile configuration loaded from RMS. */
public final class ProfileState {
    public final Vector profiles = new Vector();
    public String activeProfileId = ProviderPresets.OPENAI;
    public boolean legacyMigrated;

    /* Load diagnostics; these flags are deliberately not persisted. */
    public boolean migratedThisLoad;
    public boolean recoveredFromBackup;
    public boolean storageCorrupt;

    public ProviderProfile find(String profileId) {
        if (profileId == null) return null;
        int i;
        for (i = 0; i < profiles.size(); i++) {
            ProviderProfile profile = (ProviderProfile) profiles.elementAt(i);
            if (profileId.equals(profile.id)) return profile;
        }
        return null;
    }

    public ProviderProfile getActiveProfile() {
        ProviderProfile profile = find(activeProfileId);
        if (profile != null) return profile;
        profile = find(ProviderPresets.OPENAI);
        if (profile != null) {
            activeProfileId = profile.id;
            return profile;
        }
        return profiles.size() == 0 ? null : (ProviderProfile) profiles.elementAt(0);
    }

    public boolean setActiveProfileId(String profileId) {
        if (find(profileId) == null) return false;
        activeProfileId = profileId;
        return true;
    }

    public void replace(ProviderProfile replacement) {
        if (replacement == null) return;
        int i;
        for (i = 0; i < profiles.size(); i++) {
            ProviderProfile current = (ProviderProfile) profiles.elementAt(i);
            if (current.id.equals(replacement.id)) {
                profiles.setElementAt(replacement, i);
                return;
            }
        }
        profiles.addElement(replacement);
    }
}

