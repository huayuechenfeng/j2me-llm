package com.chihoko.j2mellm.ui;

import javax.microedition.lcdui.Image;

public interface ImageLoadListener {
    void onImageLoaded(Image image);
    void onImageLoadError(String message);
}

