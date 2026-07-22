package com.chihoko.j2mellm.ui;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

/** JSR-75 browser loaded by reflection only when the user chooses Import. */
public final class ConfigPackagePicker implements ConfigPickerController, CommandListener {
    private static final int MAX_ENTRIES = 256;
    private final Command backCommand = new Command("返回", Command.BACK, 2);
    private final Command upCommand = new Command("上级", Command.BACK, 1);
    private Display display;
    private Displayable back;
    private ConfigPickListener listener;
    private List list;
    private Vector urls;
    private String currentUrl;

    public void open(Display target, Displayable backScreen, ConfigPickListener callback) {
        display = target;
        back = backScreen;
        listener = callback;
        showRoots();
    }

    public void commandAction(Command command, Displayable source) {
        if (command == backCommand) {
            display.setCurrent(back);
        } else if (command == upCommand) {
            showParent();
        } else if (command == List.SELECT_COMMAND && list != null && list.getSelectedIndex() >= 0) {
            String url = (String) urls.elementAt(list.getSelectedIndex());
            if (url.endsWith("/")) showDirectory(url);
            else {
                display.setCurrent(back);
                listener.onConfigPicked(url);
            }
        }
    }

    private void showRoots() {
        try {
            urls = new Vector();
            list = new List("选择 .j2cfg", List.IMPLICIT);
            Enumeration roots = FileSystemRegistry.listRoots();
            while (roots.hasMoreElements() && urls.size() < MAX_ENTRIES) {
                String root = (String) roots.nextElement();
                list.append(root, null);
                urls.addElement("file:///" + root);
            }
            if (roots.hasMoreElements()) list.setTitle("选择 .j2cfg · 前256项");
            currentUrl = null;
            finishList();
        } catch (Throwable failure) {
            fail("无法读取文件系统：" + message(failure));
        }
    }

    private void showDirectory(String url) {
        FileConnection file = null;
        try {
            file = (FileConnection) Connector.open(url, Connector.READ);
            if (!file.exists() || !file.isDirectory()) throw new IOException("目录不存在");
            urls = new Vector();
            list = new List(shortTitle(url), List.IMPLICIT);
            Enumeration entries = file.list();
            boolean limited = false;
            while (entries.hasMoreElements()) {
                String name = (String) entries.nextElement();
                if (name.endsWith("/") || name.toLowerCase().endsWith(".j2cfg")) {
                    if (urls.size() >= MAX_ENTRIES) {
                        limited = true;
                        break;
                    }
                    list.append(name, null);
                    urls.addElement(url + name);
                }
            }
            if (limited) list.setTitle(shortTitle(url) + " · 前256项");
            currentUrl = url;
            finishList();
        } catch (Throwable failure) {
            fail("无法打开目录：" + message(failure));
        } finally {
            close(file);
        }
    }

    private void finishList() {
        list.addCommand(backCommand);
        if (currentUrl != null) list.addCommand(upCommand);
        list.setCommandListener(this);
        display.setCurrent(list);
    }

    private void showParent() {
        if (currentUrl == null) {
            showRoots();
            return;
        }
        String value = currentUrl.substring(0, currentUrl.length() - 1);
        int slash = value.lastIndexOf('/');
        if (slash <= "file://".length()) showRoots();
        else {
            String parent = value.substring(0, slash + 1);
            if (parent.length() <= "file:///".length()) showRoots();
            else showDirectory(parent);
        }
    }

    private String shortTitle(String url) {
        return url.length() <= 28 ? url : "…" + url.substring(url.length() - 27);
    }

    private String message(Throwable failure) {
        String value = failure.getMessage();
        return value == null ? failure.toString() : value;
    }

    private void fail(String value) {
        display.setCurrent(back);
        if (listener != null) listener.onConfigPickError(value);
    }

    private void close(FileConnection file) {
        if (file != null) try { file.close(); } catch (IOException ignored) { }
    }
}
