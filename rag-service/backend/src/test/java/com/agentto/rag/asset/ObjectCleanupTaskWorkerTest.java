package com.agentto.rag.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import com.agentto.rag.storage.InMemoryObjectStorage;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:object_cleanup_worker;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(ObjectCleanupTaskWorkerTest.Overrides.class)
class ObjectCleanupTaskWorkerTest {

    @Autowired
    private ObjectCleanupTaskWorker worker;
    @Autowired
    private ObjectCleanupTaskRepository taskRepository;
    @Autowired
    private ContentAssetRepository assetRepository;
    @Autowired
    private InMemoryObjectStorage storage;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        assetRepository.deleteAll();
        storage.clear();
    }

    @Test
    void skipsDeleteWhenAssetOwnsTheObject() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        storage.seed("owned", "bytes".getBytes(), Map.of());
        assetRepository.save(ContentAsset.stored("e".repeat(64), InMemoryObjectStorage.BUCKET, "owned", 5, "text/plain",
                now));
        ObjectCleanupTask task = taskRepository.save(ObjectCleanupTask.pending(InMemoryObjectStorage.BUCKET, "owned", now));

        worker.runOnce();

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(ObjectCleanupTask.SKIPPED);
        assertThat(storage.size()).isOne();
    }

    @Test
    void deletesOrphanAndCompletes() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        storage.seed("orphan", "bytes".getBytes(), Map.of());
        ObjectCleanupTask task = taskRepository
                .save(ObjectCleanupTask.pending(InMemoryObjectStorage.BUCKET, "orphan", now));

        worker.runOnce();

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(ObjectCleanupTask.COMPLETE);
        assertThat(storage.size()).isZero();
    }

    @Test
    void deleteFailureIsRetryable() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        storage.seed("orphan", "bytes".getBytes(), Map.of());
        storage.failNextDeletes(new IllegalStateException("minio down"));
        ObjectCleanupTask task = taskRepository
                .save(ObjectCleanupTask.pending(InMemoryObjectStorage.BUCKET, "orphan", now));

        worker.runOnce();

        ObjectCleanupTask updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ObjectCleanupTask.RETRYABLE_FAILURE);
        assertThat(updated.getLastErrorCode()).isEqualTo("OBJECT_DELETE_FAILED");
        assertThat(storage.size()).isOne();
    }

    @TestConfiguration
    static class Overrides {
        @Bean
        @Primary
        InMemoryObjectStorage memoryStorage() {
            return new InMemoryObjectStorage();
        }
    }
}
