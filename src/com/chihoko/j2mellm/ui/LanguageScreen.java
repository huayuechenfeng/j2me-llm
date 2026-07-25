package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.i18n.I18n;
import com.chihoko.j2mellm.i18n.TextId;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.List;

/** A bilingual recovery-friendly selector for the global UI language. */
public final class LanguageScreen extends List {
    public final Command applyCommand = new Command(I18n.text(TextId.APPLY), Command.OK, 1);
    public final Command backCommand = new Command(I18n.text(TextId.BACK), Command.BACK, 2);

    public LanguageScreen(CommandListener listener) {
        super(I18n.text(TextId.LANGUAGE_TITLE), List.EXCLUSIVE);
        append(I18n.text(TextId.SYSTEM_DEFAULT), null);
        append(I18n.text(TextId.SIMPLIFIED_CHINESE), null);
        append(I18n.text(TextId.ENGLISH), null);
        int selected = I18n.getPreference();
        if (selected < I18n.AUTO || selected > I18n.EN) selected = I18n.AUTO;
        setSelectedIndex(selected, true);
        addCommand(applyCommand);
        addCommand(backCommand);
        setCommandListener(listener);
    }

    public int selectedPreference() {
        int selected = getSelectedIndex();
        return selected < I18n.AUTO || selected > I18n.EN ? I18n.AUTO : selected;
    }
}
