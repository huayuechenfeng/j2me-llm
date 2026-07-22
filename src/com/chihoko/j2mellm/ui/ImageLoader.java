package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.util.Base64;
import com.chihoko.j2mellm.util.ImageDimensions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Image;

public final class ImageLoader implements Runnable {
    private static final int MAX_DOWNLOAD_BYTES = 262144;
    private static final int MAX_PREVIEW_PIXELS = 65536;
    private String source;
    private int width;
    private int height;
    private ImageLoadListener listener;

    public void load(String value, int maximumWidth, int maximumHeight, ImageLoadListener callback) {
        source = value;
        width = maximumWidth;
        height = maximumHeight;
        listener = callback;
        new Thread(this).start();
    }

    public void run() {
        try {
            byte[] data = readSource();
            ImageDimensions dimensions = ImageDimensions.parse(data);
            if (dimensions == null) {
                throw new IOException("无法安全识别图片尺寸，已跳过预览");
            }
            if (!dimensions.fitsPixelLimit(MAX_PREVIEW_PIXELS)) {
                throw new IOException("图片像素超过 65536，已跳过预览");
            }
            ensureDecodeMemory(data.length, dimensions.pixelCountOrMaximum());
            Image image = Image.createImage(data, 0, data.length);
            data = null;
            image = ImageScaler.fit(image, width, height);
            listener.onImageLoaded(image);
        } catch (Throwable failure) {
            String message = failure.getMessage();
            listener.onImageLoadError(message == null ? failure.toString() : message);
        }
    }

    private byte[] readSource() throws IOException {
        if (source == null) throw new IOException("缺少图片地址");
        if (source.startsWith("data:image/")) {
            int comma = source.indexOf(',');
            if (comma < 0 || source.substring(0, comma).indexOf(";base64") < 0) {
                throw new IOException("不支持的图片 data URL");
            }
            String encoded = source.substring(comma + 1);
            if (encoded.length() > ((MAX_DOWNLOAD_BYTES * 4) / 3) + 8) {
                throw new IOException("返回图片超过 256 KB");
            }
            byte[] decoded = Base64.decode(encoded);
            if (decoded.length > MAX_DOWNLOAD_BYTES) throw new IOException("返回图片超过 256 KB");
            return decoded;
        }
        String lower = source.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IOException("只支持 HTTP、HTTPS 或 data URL 图片");
        }
        HttpConnection connection = null;
        InputStream input = null;
        try {
            connection = (HttpConnection) Connector.open(source, Connector.READ, true);
            connection.setRequestMethod(HttpConnection.GET);
            connection.setRequestProperty("Accept", "image/png, image/jpeg, image/gif, image/webp");
            connection.setRequestProperty("Connection", "close");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("图片 HTTP " + status);
            long declared = connection.getLength();
            if (declared > MAX_DOWNLOAD_BYTES) throw new IOException("返回图片超过 256 KB");
            input = connection.openInputStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream(
                    declared > 0 ? (int) declared : 4096);
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                if (output.size() + count > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("返回图片超过 256 KB");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            if (input != null) try { input.close(); } catch (IOException ignored) { }
            if (connection != null) try { connection.close(); } catch (IOException ignored) { }
        }
    }

    private void ensureDecodeMemory(int encodedBytes, int pixels) throws IOException {
        long free = Runtime.getRuntime().freeMemory();
        long reserve = (long) encodedBytes + ((long) pixels * 4L) + 65536L;
        if (free > 0 && free < reserve) {
            throw new IOException("可用内存不足，已跳过图片预览");
        }
    }
}
