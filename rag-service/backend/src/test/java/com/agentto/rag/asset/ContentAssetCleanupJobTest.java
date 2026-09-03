package com.agentto.rag.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.agentto.rag.storage.InMemoryObjectStorage;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:content_asset_cleanup;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(ContentAssetCleanupJobTest.Overrides.class)
class ContentAssetCleanupJobTest {

    private static final byte[] BYTES = "cleanup-asset".getBytes();
    private static final String SHA = ContentAssetServiceTest.sha256(BYTES);
    private static final String KEY = CanonicalObjectKey.of(SHA);

    @Autowired
    private ContentAssetCleanupJob job;
    @Autowired
    private ContentAssetRepository repository;
    @Autowired
    private InMemoryObjectStorage storage;
    @Autowired
    private MutableClock clock;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from rag_document_version");
        repository.deleteAll();
        storage.clear();
        clock.set(Instant.parse("2026-08-31T00:00:00Z"));
    }

    @Test
    void firstUnreferencedReadyOnlyWritesTimestamp() {
        ContentAsset asset = saveReady();
        storage.seed(KEY, BYTES, Map.of("sha256", SHA));

        job.runOnce();

        ContentAsset updated = repository.findById(asset.getId()).orElseThrow();
        assertThat(updated.getStorageState()).isEqualTo(ContentAssetState.READY);
        assertThat(updated.getUnreferencedSince()).isEqualTo(clock.instant());
        assertThat(storage.size()).isOne();
    }

    @Test
    void doesNotDeleteBeforeTtl() {
        ContentAsset asset = saveReady();
        jdbcTemplate.update("update rag_content_asset set unreferenced_since = ? where id = ?",
                clock.instant().minus(Duration.ofHours(167)), asset.getId());
        storage.seed(KEY, BYTES, Map.of("sha256", SHA));

        job.runOnce();

        assertThat(repository.findById(asset.getId()).orElseThrow().getStorageState())
                .isEqualTo(ContentAssetState.READY);
        assertThat(storage.size()).isOne();
    }

    @Test
    void deletesAtExactlyOneHundredSixtyEightHours() {
        ContentAsset asset = saveReady();
        jdbcTemplate.update("update rag_content_asset set unreferenced_since = ? where id = ?",
                clock.instant().minus(Duration.ofHours(168)), asset.getId());
        storage.seed(KEY, BYTES, Map.of("sha256", SHA));

        job.runOnce();

        assertThat(repository.findById(asset.getId())).isEmpty();
        assertThat(storage.size()).isZero();
    }

    @Test
    void historicalDocumentVersionBlocksDelete() {
        ContentAsset asset = saveReady();
        jdbcTemplate.update("update rag_content_asset set unreferenced_since = ? where id = ?",
                clock.instant().minus(Duration.ofHours(168)), asset.getId());
        insertVersion(asset.getId(), SHA);
        storage.seed(KEY, BYTES, Map.of("sha256", SHA));

        job.runOnce();

        ContentAsset updated = repository.findById(asset.getId()).orElseThrow();
        assertThat(updated.getStorageState()).isEqualTo(ContentAssetState.READY);
        assertThat(updated.getUnreferencedSince()).isNull();
        assertThat(storage.size()).isOne();
    }

    @Test
    void deleteFailureKeepsRowAndSchedulesRetry() {
        ContentAsset asset = saveReady();
        jdbcTemplate.update("update rag_content_asset set unreferenced_since = ? where id = ?",
                clock.instant().minus(Duration.ofHours(168)), asset.getId());
        storage.seed(KEY, BYTES, Map.of("sha256", SHA));
        storage.failNextDeletes(new IllegalStateException("minio down"));

        job.runOnce();

        ContentAsset updated = repository.findById(asset.getId()).orElseThrow();
        assertThat(updated.getStorageState()).isEqualTo(ContentAssetState.DELETE_FAILED);
        assertThat(updated.getLastErrorCode()).isEqualTo("OBJECT_DELETE_FAILED");
        assertThat(updated.getDeleteAttemptCount()).isEqualTo(1);
        assertThat(updated.getNextDeleteRetryAt()).isAfter(clock.instant());
        assertThat(storage.size()).isOne();
    }

    @Test
    void expiredPendingWithValidObjectBecomesReady() {
        ContentAsset pending = repository.save(ContentAsset.pending(SHA, InMemoryObjectStorage.BUCKET, KEY,
                BYTES.length, "text/plain", "owner", clock.instant().minus(Duration.ofMinutes(1)), clock.instant()));
        storage.seed(KEY, BYTES, Map.of("sha256", SHA));

        job.runOnce();

        assertThat(repository.findById(pending.getId()).orElseThrow().getStorageState())
                .isEqualTo(ContentAssetState.READY);
    }

    @Test
    void expiredPendingWithMissingObjectReleasesLeaseAndStaysPending() {
        ContentAsset pending = repository.save(ContentAsset.pending(SHA, InMemoryObjectStorage.BUCKET, KEY,
                BYTES.length, "text/plain", "owner", clock.instant().minus(Duration.ofMinutes(1)), clock.instant()));

        job.runOnce();

        ContentAsset updated = repository.findById(pending.getId()).orElseThrow();
        assertThat(updated.getStorageState()).isEqualTo(ContentAssetState.PENDING);
        assertThat(updated.getLeaseOwner()).isNull();
        assertThat(storage.size()).isZero();
    }

    @Test
    void expiredPendingWithoutMetadataRecomputesAndBecomesReadyWhenBytesMatch() {
        ContentAsset pending = repository.save(ContentAsset.pending(SHA, InMemoryObjectStorage.BUCKET, KEY,
                BYTES.length, "text/plain", "owner", clock.instant().minus(Duration.ofMinutes(1)), clock.instant()));
        storage.seedWithoutMetadata(KEY, BYTES);

        job.runOnce();

        assertThat(repository.findById(pending.getId()).orElseThrow().getStorageState())
                .isEqualTo(ContentAssetState.READY);
        assertThat(storage.getCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void expiredPendingWithoutMetadataAndWrongBytesStaysPending() {
        ContentAsset pending = repository.save(ContentAsset.pending(SHA, InMemoryObjectStorage.BUCKET, KEY,
                BYTES.length, "text/plain", "owner", clock.instant().minus(Duration.ofMinutes(1)), clock.instant()));
        storage.seedWithoutMetadata(KEY, "not-the-original-bytes".getBytes());

        job.runOnce();

        ContentAsset updated = repository.findById(pending.getId()).orElseThrow();
        assertThat(updated.getStorageState()).isEqualTo(ContentAssetState.PENDING);
        assertThat(updated.getLeaseOwner()).isNull();
        assertThat(storage.size()).isOne();
    }

    @Test
    void shaMatchOnVersionWithoutAssetIdStillProtects() {
        ContentAsset asset = saveReady();
        jdbcTemplate.update("update rag_content_asset set unreferenced_since = ? where id = ?",
                clock.instant().minus(Duration.ofHours(168)), asset.getId());
        insertVersion(null, SHA);
        storage.seed(KEY, BYTES, Map.of("sha256", SHA));

        job.runOnce();

        assertThat(repository.findById(asset.getId()).orElseThrow().getStorageState())
                .isEqualTo(ContentAssetState.READY);
    }

    private ContentAsset saveReady() {
        return repository.save(ContentAsset.stored(SHA, InMemoryObjectStorage.BUCKET, KEY, BYTES.length, "text/plain",
                clock.instant()));
    }

    private void insertVersion(Long assetId, String sha256) {
        jdbcTemplate.update("""
                insert into rag_document_version
                (document_id, version_no, original_filename, content_type, file_size, sha256,
                 object_bucket, object_key, processing_status, chunk_count, created_by, created_at, content_asset_id)
                values (1, 1, 'a.docx', 'text/plain', 4, ?, 'test-bucket', ?, 'READY', 0, 1, current_timestamp, ?)
                """, sha256, "legacy/" + sha256.substring(0, 8), assetId);
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
