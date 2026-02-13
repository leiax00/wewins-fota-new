package com.wewins.fota.storage.core;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Local file staging and storage transfer service.
 */
public interface FileTransferService {

    Path createStagingFile(String prefix, String suffix) throws IOException;

    String transferToStorage(Path localFile, String objectKey, String contentType, boolean deleteAfterTransfer);

    void deleteStagingFile(Path localFile);
}
