package com.chihoko.j2mellm.net;

import java.util.Vector;

/**
 * Receives one on-demand model catalog request. Network completion callbacks
 * run on the worker thread; validation and busy errors may be synchronous.
 */
public interface ModelCatalogListener {
    void onModels(Vector modelIds, boolean truncated);
    void onError(String message);
}
