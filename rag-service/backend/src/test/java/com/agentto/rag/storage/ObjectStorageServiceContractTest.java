package com.agentto.rag.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ObjectStorageServiceContractTest {

    private final InMemoryObjectStorage storage = new InMemoryObjectStorage();

    @Test
    void expectedLocationUsesConfiguredBucketAndCallerKey() {
        assertThat(storage.expectedLocation("content-assets/sha256/ab/ab")).isEqualTo(
                new StoredObject(InMemoryObjectStorage.BUCKET, "content-assets/sha256/ab/ab"));
    }

    @Test
    void streamingPutStoresMetadataAndStatCanReadItBack() {
        byte[] bytes = "payload".getBytes();
        storage.put("k1", new ByteArrayInputStream(bytes), bytes.length, "text/plain", Map.of("sha256", "abc"));

        ObjectStat stat = storage.stat(InMemoryObjectStorage.BUCKET, "k1").orElseThrow();
        assertThat(stat.size()).isEqualTo(bytes.length);
        assertThat(stat.sha256()).contains("abc");
        assertThat(storage.bytes("k1")).containsExactly(bytes);
    }

    @Test
    void statMissingObjectIsEmptyAndDeleteIsTargeted() {
        storage.put("keep", "keep".getBytes(), "text/plain");
        storage.put("drop", "drop".getBytes(), "text/plain");

        assertThat(storage.stat(InMemoryObjectStorage.BUCKET, "missing")).isEmpty();
        storage.delete(InMemoryObjectStorage.BUCKET, "drop");
        assertThat(storage.stat(InMemoryObjectStorage.BUCKET, "drop")).isEmpty();
        assertThat(storage.bytes("keep")).isEqualTo("keep".getBytes());
    }
}
