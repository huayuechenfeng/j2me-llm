package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.i18n.I18n;
import com.chihoko.j2mellm.i18n.TextId;
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
                throw new IOException(I18n.text(TextId.IMAGE_DIMENSIONS_UNSAFE));
            }
            if (!dimensions.fitsPixelLimit(MAX_PREVIEW_PIXELS)) {
                throw new IOException(I18n.text(TextId.IMAGE_PIXELS_PREVIEW_SKIPPED));
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
        if (source == null) throw new IOException(I18n.text(TextId.IMAGE_SOURCE_MISSING));
        if (source.startsWith("data:image/")) {
            int comma = source.indexOf(',');
            if (comma < 0 || source.substring(0, comma).indexOf(";base64") < 0) {
                throw new IOException(I18n.text(TextId.IMAGE_DATA_URL_UNSUPPORTED));
            }
            String encoded = source.substring(comma + 1);
            if (encoded.length() > ((MAX_DOWNLOAD_BYTES * 4) / 3) + 8) {
                throw new IOException(I18n.text(TextId.RETURNED_IMAGE_TOO_LARGE));
            }
            byte[] decoded = Base64.decode(encoded);
            if (decoded.length > MAX_DOWNLOAD_BYTES) {
                throw new IOException(I18n.text(TextId.RETURNED_IMAGE_TOO_LARGE));
            }
            return decoded;
        }
        String lower = source.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IOException(I18n.text(TextId.IMAGE_PROTOCOL_UNSUPPORTED));
        }
        HttpConnection connection = null;
        InputStream input = null;
        try {
            connection = (HttpConnection) Connector.open(source, Connector.READ, true);
            connection.setRequestMethod(HttpConnection.GET);
            connection.setRequestProperty("Accept", "image/png, image/jpeg, image/gif, image/webp");
            connection.setRequestProperty("Connection", "close");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Image HTTP " + status);
            long declared = connection.getLength();
            if (declared > MAX_DOWNLOAD_BYTES) {
                throw new IOException(I18n.text(TextId.RETURNED_IMAGE_TOO_LARGE));
            }
            input = connection.openInputStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream(
                    declared > 0 ? (int) declared : 4096);
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                if (output.size() + count > MAX_DOWNLOAD_BYTES) {
                    throw new IOException(I18n.text(TextId.RETURNED_IMAGE_TOO_LARGE));
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
            throw new IOException(I18n.text(TextId.IMAGE_PREVIEW_LOW_MEMORY));
        }
    }
}
