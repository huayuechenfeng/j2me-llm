package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.i18n.I18n;
import com.chihoko.j2mellm.i18n.TextId;
import com.chihoko.j2mellm.model.ImageAttachment;
import com.chihoko.j2mellm.util.ImageDimensions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

public final class ImagePicker implements ImagePickerController, CommandListener {
    private static final int MAX_IMAGE_BYTES = 98304;
    private static final int MAX_PREVIEW_PIXELS = 65536;
    private static final int MAX_ENTRIES = 256;
    private final Command backCommand = new Command(
            I18n.text(TextId.BACK), Command.BACK, 2);
    private final Command upCommand = new Command(I18n.text(TextId.UP), Command.BACK, 1);
    private Display display;
    private Displayable back;
    private ImagePickListener listener;
    private List list;
    private Vector urls;
    private String currentUrl;

    public void open(Display targetDisplay, Displayable backScreen, ImagePickListener callback) {
        display = targetDisplay;
        back = backScreen;
        listener = callback;
        showRoots();
    }

    public void commandAction(Command command, Displayable source) {
        if (command == backCommand) {
            display.setCurrent(back);
            return;
        }
        if (command == upCommand) {
            showParent();
            return;
        }
        if (command == List.SELECT_COMMAND && list != null && list.getSelectedIndex() >= 0) {
            String url = (String) urls.elementAt(list.getSelectedIndex());
            if (url.endsWith("/")) showDirectory(url); else readImage(url);
        }
    }

    private void showRoots() {
        try {
            urls = new Vector();
            list = new List(I18n.text(TextId.SELECT_IMAGE_LOCATION), List.IMPLICIT);
            Enumeration roots = FileSystemRegistry.listRoots();
            while (roots.hasMoreElements() && urls.size() < MAX_ENTRIES) {
                String root = (String) roots.nextElement();
                list.append(root, null);
                urls.addElement("file:///" + root);
            }
            if (roots.hasMoreElements()) {
                list.setTitle(I18n.text(TextId.SELECT_IMAGE_LOCATION)
                        + I18n.text(TextId.FIRST_256_SUFFIX));
            }
            currentUrl = null;
            finishList();
        } catch (Throwable failure) {
            fail(I18n.text(TextId.READ_FILESYSTEM_FAILED_PREFIX)
                    + I18n.error(message(failure)));
        }
    }

    private void showDirectory(String url) {
        FileConnection file = null;
        try {
            file = (FileConnection) Connector.open(url, Connector.READ);
            if (!file.exists() || !file.isDirectory()) {
                throw new IOException(I18n.text(TextId.DIRECTORY_MISSING));
            }
            urls = new Vector();
            list = new List(shortTitle(url), List.IMPLICIT);
            Enumeration entries = file.list();
            boolean limited = false;
            while (entries.hasMoreElements()) {
                String name = (String) entries.nextElement();
                if (name.endsWith("/") || isImage(name)) {
                    if (urls.size() >= MAX_ENTRIES) {
                        limited = true;
                        break;
                    }
                    list.append(name, null);
                    urls.addElement(url + name);
                }
            }
            if (limited) {
                list.setTitle(shortTitle(url) + I18n.text(TextId.FIRST_256_SUFFIX));
            }
            currentUrl = url;
            finishList();
        } catch (Throwable failure) {
            fail(I18n.text(TextId.OPEN_DIRECTORY_FAILED_PREFIX)
                    + I18n.error(message(failure)));
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
        String withoutSlash = currentUrl.substring(0, currentUrl.length() - 1);
        int slash = withoutSlash.lastIndexOf('/');
        if (slash <= "file://".length()) {
            showRoots();
        } else {
            String parent = withoutSlash.substring(0, slash + 1);
            if (parent.length() <= "file:///".length()) showRoots();
            else showDirectory(parent);
        }
    }

    private void readImage(String url) {
        FileConnection file = null;
        InputStream input = null;
        try {
            file = (FileConnection) Connector.open(url, Connector.READ);
            long size = file.fileSize();
            if (size > MAX_IMAGE_BYTES) {
                throw new IOException(I18n.text(TextId.IMAGE_TOO_LARGE));
            }
            ensureMemory(size);
            input = file.openInputStream();
            byte[] data = size > 0 ? readKnownSize(input, (int) size) : readUnknownSize(input);
            ImageDimensions dimensions = ImageDimensions.parse(data);
            if (dimensions == null) {
                throw new IOException(I18n.text(TextId.IMAGE_DIMENSIONS_UNKNOWN));
            }
            if (!dimensions.fitsPixelLimit(MAX_PREVIEW_PIXELS)) {
                throw new IOException(I18n.text(TextId.IMAGE_PIXELS_TOO_LARGE));
            }
            ensurePreviewMemory(data.length, dimensions.pixelCountOrMaximum());
            String name = file.getName();
            ImageAttachment attachment = new ImageAttachment(name, mime(dimensions), data);
            display.setCurrent(back);
            listener.onImagePicked(attachment);
        } catch (Throwable failure) {
            fail(I18n.text(TextId.READ_IMAGE_FAILED_PREFIX)
                    + I18n.error(message(failure)));
        } finally {
            if (input != null) try { input.close(); } catch (IOException ignored) { }
            close(file);
        }
    }

    private byte[] readKnownSize(InputStream input, int size) throws IOException {
        byte[] data;
        try {
            data = new byte[size];
        } catch (OutOfMemoryError failure) {
            throw new IOException(I18n.text(TextId.CHOOSE_SMALLER_IMAGE));
        }
        int offset = 0;
        while (offset < data.length) {
            int count = input.read(data, offset, data.length - offset);
            if (count < 0) throw new IOException(I18n.text(TextId.IMAGE_READ_INCOMPLETE));
            if (count > 0) offset += count;
        }
        return data;
    }

    private byte[] readUnknownSize(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count == 0) continue;
            if (output.size() + count > MAX_IMAGE_BYTES) {
                throw new IOException(I18n.text(TextId.IMAGE_TOO_LARGE));
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void ensureMemory(long size) throws IOException {
        if (size <= 0) return;
        long free = Runtime.getRuntime().freeMemory();
        long reserve = size * 3L + 65536L;
        if (free > 0 && free < reserve) {
            throw new IOException(I18n.text(TextId.CHOOSE_SMALLER_IMAGE));
        }
    }

    private void ensurePreviewMemory(int encodedBytes, int pixels) throws IOException {
        long free = Runtime.getRuntime().freeMemory();
        long reserve = (long) encodedBytes + ((long) pixels * 4L) + 65536L;
        if (free > 0 && free < reserve) {
            throw new IOException(I18n.text(TextId.CHOOSE_SMALLER_IMAGE));
        }
    }

    private boolean isImage(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp");
    }

    private String mime(ImageDimensions dimensions) {
        if (ImageDimensions.PNG.equals(dimensions.format)) return "image/png";
        if (ImageDimensions.GIF.equals(dimensions.format)) return "image/gif";
        if (ImageDimensions.WEBP.equals(dimensions.format)) return "image/webp";
        return "image/jpeg";
    }

    private String shortTitle(String url) {
        if (url.length() <= 28) return url;
        return "…" + url.substring(url.length() - 27);
    }

    private String message(Throwable failure) {
        String value = failure.getMessage();
        return value == null ? failure.toString() : value;
    }

    private void fail(String value) {
        display.setCurrent(back);
        if (listener != null) listener.onImagePickError(value);
    }

    private void close(FileConnection file) {
        if (file != null) {
            try { file.close(); } catch (IOException ignored) { }
        }
    }
}
