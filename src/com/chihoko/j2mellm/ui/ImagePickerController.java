package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.model.ResourceLimits;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;

public interface ImagePickerController {
    void configureLimits(ResourceLimits limits);
    void open(Display display, Displayable back, ImagePickListener listener);
}
