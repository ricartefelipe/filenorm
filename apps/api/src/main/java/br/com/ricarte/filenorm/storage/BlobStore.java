package br.com.ricarte.filenorm.storage;

import java.io.InputStream;

public interface BlobStore {

    String save(String relativePath, InputStream input, long size);

    InputStream open(String relativePath);

    void delete(String relativePath);
}
