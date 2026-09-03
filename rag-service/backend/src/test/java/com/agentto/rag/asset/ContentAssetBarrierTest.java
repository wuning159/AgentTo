package com.agentto.rag.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.agentto.rag.storage.InMemoryObjectStorage;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:content_asset_barrier;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(ContentAssetBarrierTest.Overrides.class)
class ContentAssetBarrierTest {

    private static final byte[] BYTES = "barrier-asset".getBytes();
    private static final String SHA = ContentAssetServiceTest.sha256(BYTES);
    private static final String KEY = CanonicalObjectKey.of(SHA);

    @Autowired
    private ContentAssetService service;
    @Autowired
    private ContentAssetCleanupJob job;
    @Autowired
    private ContentAssetRepository repository;
    @Autowired
    private InMemoryObjectStorage storage;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from rag_document_version");
        repository.deleteAll();
        storage.clear();
        clock.set(Instant.parse("2026-08-31T00:00:00Z"));
    }

    @Test
    void referenceAndCleanupSerializeOnTheSameRow() throws Exception {
        ContentAsset asset = repository.save(ContentAsset.stored(SHA, InMemoryObjectStorage.BUCKET, KEY, BYTES.length,
                "text/plain", clock.instant()));
        jdbcTemplate.update("update rag_content_asset set unreferenced_since = ? where id = ?",
                clock.instant().minus(Duration.ofHours(168)), asset.getId());
        storage.seed(KEY, BYTES, Map.of("sha256", SHA));

        CyclicBarrier start = new CyclicBarrier(2);
        CountDownLatch referenced = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ContentAsset> reference = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                ContentAsset ready = service.requireReadyForReference(asset.getId());
                jdbcTemplate.update("""
                        insert into rag_document_version
                        (document_id, version_no, original_filename, content_type, file_size, sha256,
                         object_bucket, object_key, processing_status, chunk_count, created_by, created_at, content_asset_id)
                        values (9, 1, 'b.docx', 'text/plain', ?, ?, ?, ?, 'READY', 0, 1, current_timestamp, ?)
                        """, BYTES.length, SHA, InMemoryObjectStorage.BUCKET, KEY, asset.getId());
                referenced.countDown();
                return ready;
            });
            Future<?> cleanup = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                referenced.await(2, TimeUnit.SECONDS);
                job.runOnce();
                return null;
            });
            assertThat(reference.get(10, TimeUnit.SECONDS).getStorageState()).isEqualTo(ContentAssetState.READY);
            cleanup.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        ContentAsset remaining = repository.findById(asset.getId()).orElseThrow();
        assertThat(remaining.getStorageState()).isEqualTo(ContentAssetState.READY);
        assertThat(storage.size()).isOne();
    }

    @TestConfiguration
    static class Overrides {
        @Bean
        @Primary
        InMemoryObjectStorage memoryStorage() {
            return new InMemoryObjectStorage();
        }

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-08-31T00:00:00Z"));
        }
    }
}
