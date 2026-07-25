package br.com.ricarte.filenorm.storage;

import br.com.ricarte.filenorm.config.FilenormProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;

@Component
public class LocalBlobStore implements BlobStore {

    private final Path root;

    public LocalBlobStore(FilenormProperties properties) {
        this.root = Path.of(properties.storage().path()).toAbsolutePath().normalize();
    }

    @Override
    public String save(String relativePath, InputStream input, long size) {
        try {
            Path target = resolve(relativePath);
            Files.createDirectories(target.getParent());
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            return relativePath;
        } catch (IOException ex) {
            throw new IllegalStateException("blob_save_failed", ex);
        }
    }

    @Override
    public InputStream open(String relativePath) {
        try {
            return Files.newInputStream(resolve(relativePath));
        } catch (IOException ex) {
            throw new IllegalStateException("blob_open_failed", ex);
        }
    }

    @Override
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ex) {
            throw new IllegalStateException("blob_delete_failed", ex);
        }
    }

    private Path resolve(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("invalid_blob_path");
        }
        return resolved;
    }
}
