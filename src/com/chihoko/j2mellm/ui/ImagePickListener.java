package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.model.ImageAttachment;

public interface ImagePickListener {
    void onImagePicked(ImageAttachment attachment);
    void onImagePickError(String message);
}

