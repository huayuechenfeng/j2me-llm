package com.chihoko.j2mellm.model;

import javax.microedition.lcdui.Image;

/** Allocated only when a message actually contains media. */
final class MessageMedia {
    String name = "";
    String mimeType = "";
    byte[] data;
    String source = "";
    String status = "";
    Image preview;
}
