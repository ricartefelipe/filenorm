package br.com.ricarte.filenorm.storage;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

public interface BlobStore {

    String save(String relativePath, InputStream input, long size);

    InputStream open(String relativePath);

    void delete(String relativePath);

    /** Caminhos relativos de arquivos não modificados desde {@code cutoff}. */
    List<String> listOlderThan(Instant cutoff);
}
