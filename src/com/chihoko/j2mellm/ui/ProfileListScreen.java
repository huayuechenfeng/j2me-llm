package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.model.ProfileState;
import com.chihoko.j2mellm.model.ProviderProfile;

import java.util.Vector;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.List;

/** Selects one of the independently persisted provider profiles. */
public final class ProfileListScreen extends List {
    public final Command settingsCommand = new Command("设置", Command.SCREEN, 1);
    public final Command modelsCommand = new Command("模型列表", Command.SCREEN, 2);
    public final Command importCommand = new Command("导入配置", Command.SCREEN, 3);
    public final Command exportCommand = new Command("导出配置", Command.SCREEN, 4);
    public final Command backCommand = new Command("返回", Command.BACK, 5);

    private final ProfileState state;
    private final Vector displayedProfileIds = new Vector();

    public ProfileListScreen(ProfileState profileState, CommandListener listener) {
        super("模型档案", List.IMPLICIT);
        state = profileState;
        addCommand(settingsCommand);
        addCommand(modelsCommand);
        addCommand(importCommand);
        addCommand(exportCommand);
        addCommand(backCommand);
        setCommandListener(listener);
        refresh();
    }

    /** Rebuilds labels after profile data or the active selection changes. */
    public void refresh() {
        String wanted = selectedProfileId();
        if (wanted == null) wanted = state.activeProfileId;
        deleteAll();
        displayedProfileIds.removeAllElements();

        int selected = -1;
        int i;
        for (i = 0; i < state.profiles.size(); i++) {
            ProviderProfile profile = (ProviderProfile) state.profiles.elementAt(i);
            displayedProfileIds.addElement(profile.id);
            append(label(profile), null);
            if (profile.id.equals(wanted)) selected = i;
        }
        if (displayedProfileIds.size() == 0) {
            append("没有可用档案", null);
        } else {
            if (selected < 0) selected = 0;
            setSelectedIndex(selected, true);
        }
    }

    /** Returns null for the empty-state placeholder. */
    public String selectedProfileId() {
        int selected = getSelectedIndex();
        if (selected < 0 || selected >= displayedProfileIds.size()) return null;
        return (String) displayedProfileIds.elementAt(selected);
    }

    public String getSelectedProfileId() {
        return selectedProfileId();
    }

    private String label(ProviderProfile profile) {
        StringBuffer label = new StringBuffer();
        label.append(profile.id.equals(state.activeProfileId) ? "* " : "  ");
        label.append(profile.displayName());
        String model = profile.model == null ? "" : profile.model.trim();
        if (model.length() > 0 && !model.equals(profile.displayName())) {
            label.append("  [").append(model).append(']');
        }
        if (!profile.isReady()) label.append("  (未配置)");
        return label.toString();
    }
}
