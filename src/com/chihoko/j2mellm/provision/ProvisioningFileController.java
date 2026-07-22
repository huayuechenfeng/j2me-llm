package com.chihoko.j2mellm.provision;

import java.io.IOException;

/** JSR-75-free facade so file support can be loaded only when requested. */
public interface ProvisioningFileController {
    ProvisioningPackage importFile(String fileUrl) throws IOException;
    void exportFile(String fileUrl, ProvisioningPackage config) throws IOException;
    void deleteFile(String fileUrl) throws IOException;
    String defaultExportUrl() throws IOException;
}
