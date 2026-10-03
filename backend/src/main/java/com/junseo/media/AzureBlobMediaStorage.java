package com.junseo.media;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.core.util.BinaryData;
import java.util.Optional;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/** Private objects; downloads always pass through the API's media authorization. */
public class AzureBlobMediaStorage implements MediaStorage {
    private final BlobContainerClient container;
    public AzureBlobMediaStorage(BlobContainerClient container) { this.container = container; }
    @Override public void put(String key, byte[] bytes) {
        validate(key);
        var blob = container.getBlobClient(key);
        blob.upload(BinaryData.fromBytes(bytes), true);
        blob.setHttpHeaders(new BlobHttpHeaders().setContentType(key.endsWith(".png") ? "image/png" : "image/jpeg"));
    }
    @Override public Optional<Resource> get(String key) {
        validate(key);
        try { return Optional.of(new ByteArrayResource(container.getBlobClient(key).downloadContent().toBytes())); }
        catch (BlobStorageException e) { if (e.getStatusCode() == 404) return Optional.empty(); throw e; }
    }
    @Override public void delete(String key) { validate(key); container.getBlobClient(key).deleteIfExists(); }
    private static void validate(String key) {
        if (key == null || key.startsWith("/") || key.contains("..") || key.contains("\\")) throw new IllegalArgumentException("Invalid media key");
    }
}
