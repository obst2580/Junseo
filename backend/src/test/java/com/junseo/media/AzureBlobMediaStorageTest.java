package com.junseo.media;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import org.junit.jupiter.api.Test;

class AzureBlobMediaStorageTest {

    @Test
    void deleteIsBestEffort() {
        BlobContainerClient container = mock(BlobContainerClient.class);
        BlobClient failing = mock(BlobClient.class);
        BlobClient next = mock(BlobClient.class);
        when(container.getBlobClient("1/full.jpg")).thenReturn(failing);
        when(container.getBlobClient("1/thumb.jpg")).thenReturn(next);
        when(failing.deleteIfExists()).thenThrow(new IllegalStateException("503 from Blob"));
        AzureBlobMediaStorage storage = new AzureBlobMediaStorage(container);

        // Runs after the commit: an outage must not surface as a 500 or skip the other files
        assertThatCode(() -> storage.delete("1/full.jpg")).doesNotThrowAnyException();
        storage.delete("1/thumb.jpg");
        verify(next).deleteIfExists();
    }
}
