package com.chihoko.j2mellm.provision;

import com.chihoko.j2mellm.model.SearchConfig;

import java.util.Vector;

/** A decoded .j2cfg package. */
public final class ProvisioningPackage {
    private String activeProfileId = "";
    private final Vector profiles = new Vector();
    private SearchConfig searchConfig;

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

    public boolean hasSearchConfig() {
        return searchConfig != null;
    }

    public SearchConfig getSearchConfig() {
        return searchConfig == null ? null : searchConfig.copy();
    }

    public void setSearchConfig(SearchConfig value) {
        searchConfig = value == null ? null : value.copy();
    }
}
