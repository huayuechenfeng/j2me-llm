package com.chihoko.j2mellm.ui;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;

public interface ImagePickerController {
    void open(Display display, Displayable back, ImagePickListener listener);
}

