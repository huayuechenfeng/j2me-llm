package com.chihoko.j2mellm.ui;

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
    private final Command backCommand = new Command("返回", Command.BACK, 2);
    private final Command upCommand = new Command("上级", Command.BACK, 1);
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
            list = new List("选择图片位置", List.IMPLICIT);
            Enumeration roots = FileSystemRegistry.listRoots();
            while (roots.hasMoreElements() && urls.size() < MAX_ENTRIES) {
                String root = (String) roots.nextElement();
                list.append(root, null);
                urls.addElement("file:///" + root);
            }
            if (roots.hasMoreElements()) list.setTitle("选择图片位置 · 前256项");
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
                if (name.endsWith("/") || isImage(name)) {
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
            if (size > MAX_IMAGE_BYTES) throw new IOException("图片超过 96 KB，请先压缩");
            ensureMemory(size);
            input = file.openInputStream();
            byte[] data = size > 0 ? readKnownSize(input, (int) size) : readUnknownSize(input);
            ImageDimensions dimensions = ImageDimensions.parse(data);
            if (dimensions == null) {
                throw new IOException("无法识别图片尺寸，为防止内存溢出未载入");
            }
            if (!dimensions.fitsPixelLimit(MAX_PREVIEW_PIXELS)) {
                throw new IOException("图片像素超过 65536，请先缩小到约 256×256");
            }
            ensurePreviewMemory(data.length, dimensions.pixelCountOrMaximum());
            String name = file.getName();
            ImageAttachment attachment = new ImageAttachment(name, mime(dimensions), data);
            display.setCurrent(back);
            listener.onImagePicked(attachment);
        } catch (Throwable failure) {
            fail("无法读取图片：" + message(failure));
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
            throw new IOException("可用内存不足，请选择更小的图片");
        }
        int offset = 0;
        while (offset < data.length) {
            int count = input.read(data, offset, data.length - offset);
            if (count < 0) throw new IOException("图片读取不完整");
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
                throw new IOException("图片超过 96 KB，请先压缩");
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
            throw new IOException("可用内存不足，请选择更小的图片");
        }
    }

    private void ensurePreviewMemory(int encodedBytes, int pixels) throws IOException {
        long free = Runtime.getRuntime().freeMemory();
        long reserve = (long) encodedBytes + ((long) pixels * 4L) + 65536L;
        if (free > 0 && free < reserve) {
            throw new IOException("可用内存不足，请选择更小的图片");
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
