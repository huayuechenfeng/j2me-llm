package com.chihoko.j2mellm.net;

import com.chihoko.j2mellm.model.SearchBundle;

public interface SearchListener {
    void onResults(SearchBundle results);
    void onError(String error);
}
