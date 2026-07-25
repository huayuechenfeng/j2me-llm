package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.i18n.I18n;
import com.chihoko.j2mellm.i18n.TextId;
import com.chihoko.j2mellm.model.ProviderProfile;

import java.util.Vector;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.List;

/** Displays only the explicitly fetched, memory-capped model cache. */
public final class ModelListScreen extends List {
    public final Command refreshCommand = new Command(I18n.text(TextId.FETCH_ONLINE), Command.SCREEN, 1);
    public final Command backCommand = new Command(I18n.text(TextId.BACK), Command.BACK, 2);

    private ProviderProfile profile;
    private final Vector displayedModels = new Vector();

    public ModelListScreen(ProviderProfile providerProfile, CommandListener listener) {
        super(I18n.text(TextId.SELECT_MODEL), List.IMPLICIT);
        profile = providerProfile;
        addCommand(refreshCommand);
        addCommand(backCommand);
        setCommandListener(listener);
        refresh();
    }

    /** Refreshes the list from the profile cache without any network operation. */
    public void refresh() {
        deleteAll();
        displayedModels.removeAllElements();
        int selected = -1;
        int i;
        for (i = 0; i < profile.cachedModels.size(); i++) {
            String model = (String) profile.cachedModels.elementAt(i);
            if (model == null || model.length() == 0) continue;
            displayedModels.addElement(model);
            append(model, null);
            if (model.equals(profile.model)) selected = displayedModels.size() - 1;
        }
        if (displayedModels.size() == 0) {
            append(I18n.text(TextId.NO_MODEL_CACHE), null);
        } else if (selected >= 0) {
            setSelectedIndex(selected, true);
        }
    }

    public void refresh(ProviderProfile providerProfile) {
        profile = providerProfile;
        refresh();
    }

    /** Returns null when the empty-state instruction is selected. */
    public String selectedModel() {
        int selected = getSelectedIndex();
        if (selected < 0 || selected >= displayedModels.size()) return null;
        return (String) displayedModels.elementAt(selected);
    }

    public String getSelectedModel() {
        return selectedModel();
    }
}
