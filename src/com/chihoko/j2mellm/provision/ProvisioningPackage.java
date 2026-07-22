package com.chihoko.j2mellm.provision;

import java.util.Vector;

/** A decoded .j2cfg package. */
public final class ProvisioningPackage {
    private String activeProfileId = "";
    private final Vector profiles = new Vector();

    public String getActiveProfileId() {
        return activeProfileId;
    }

    public void setActiveProfileId(String value) {
        activeProfileId = value == null ? "" : value;
    }

    public Vector getProfiles() {
        return profiles;
    }

    public void addProfile(ProvisioningProfile profile) {
        if (profile != null) profiles.addElement(profile);
    }
}
