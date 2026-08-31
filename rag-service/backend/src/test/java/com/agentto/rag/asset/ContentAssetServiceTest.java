package com.agentto.rag.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:content_asset_service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(ContentAssetServiceTest.Overrides.class)
class ContentAssetServiceTest {

    private static final byte[] BYTES = "hello-asset".getBytes();
    private static final String SHA = sha256(BYTES);
    private static final String KEY = CanonicalObjectKey.of(SHA);

    @Autowired
    private ContentAssetService service;
    @Autowired
    private ContentAssetRepository repository;
    @Autowired
    private InMemoryObjectStorage storage;
    @Autowired
    private MutableClock clock;
    @Autowired
    private ControllablePendingWaiter waiter;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        storage.clear();
        waiter.reset();
        clock.set(Instant.parse("2026-08-31T00:00:00Z"));
    }

    @Test
    void readyReuseStatsOriginalLocationFirstAndTouchesRow() {
        Instant older = Instant.parse("2026-08-01T00:00:00Z");
        ContentAsset existing = repository.save(ContentAsset.stored(SHA, InMemoryObjectStorage.BUCKET, KEY, BYTES.length,
                "text/plain", older));
        jdbcTemplate.update("update rag_content_asset set unreferenced_since = ? where id = ?",
                Instant.parse("2026-08-20T00:00:00Z"), existing.getId());
        storage.seed(KEY, BYTES, Map.of("sha256", SHA));

        ContentAsset reused = service.storeOrReuse(doc(), "caller-should-be-ignored");

        assertThat(reused.getId()).isEqualTo(existing.getId());
        assertThat(reused.getStorageState()).isEqualTo(ContentAssetState.READY);
        assertThat(storage.putCount()).isZero();
        assertThat(storage.statCount()).isEqualTo(1);
        assertThat(columnInstant(existing.getId(), "last_touched_at")).isEqualTo(clock.instant());
        assertThat(columnInstant(existing.getId(), "unreferenced_since")).isNull();
    }

    @Test
    void readyReuseFailsClosedWhenShaMetadataMismatches() {
        repository.save(ContentAsset.stored(SHA, InMemoryObjectStorage.BUCKET, KEY, BYTES.length, "text/plain",
                clock.instant()));
        storage.seed(KEY, BYTES, Map.of("sha256", "0".repeat(64)));

        assertThatThrownBy(() -> service.storeOrReuse(doc()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("存储损坏");
        assertThat(storage.putCount()).isZero();
    }

    @Test
    void readyReuseRecomputesWhenShaMetadataMissing() {
        repository.save(ContentAsset.stored(SHA, InMemoryObjectStorage.BUCKET, KEY, BYTES.length, "text/plain",
                clock.instant()));
        storage.seedWithoutMetadata(KEY, BYTES);

        ContentAsset reused = service.storeOrReuse(doc());

        assertThat(reused.getStorageState()).isEqualTo(ContentAssetState.READY);
        assertThat(storage.getCount()).isEqualTo(1);
        assertThat(storage.putCount()).isZero();
    }

    @Test
    void newUploadWritesCanonicalKeyWithMetadataAndIgnoresCallerKey() {
        ContentAsset created = service.storeOrReuse(doc(), "manual/random.docx");

        assertThat(created.getObjectKey()).isEqualTo(KEY);
        assertThat(created.getStorageState()).isEqualTo(ContentAssetState.READY);
        assertThat(created.getLeaseOwner()).isNull();
        assertThat(storage.bytes(KEY)).containsExactly(BYTES);
        assertThat(storage.putCount()).isOne();
        assertThat(storage.statCount()).isEqualTo(2);
        assertThat(storage.stat(InMemoryObjectStorage.BUCKET, KEY).orElseThrow().sha256()).contains(SHA);
        assertThat(repository.findBySha256(SHA)).isPresent();
    }

    @Test
    void existingCanonicalObjectIsVerifiedAndNotOverwritten() {
        storage.seed(KEY, BYTES, Map.of("sha256", SHA));
        int statsBefore = storage.statCount();

        ContentAsset created = service.storeOrReuse(doc());

        assertThat(created.getStorageState()).isEqualTo(ContentAssetState.READY);
        assertThat(storage.putCount()).isZero();
        assertThat(storage.statCount() - statsBefore).isEqualTo(1);
    }

    @Test
    void mismatchedCanonicalObjectFailsClosedAndLeavesPendingRow() {
        storage.seed(KEY, "other".getBytes(), Map.of("sha256", "1".repeat(64)));

        assertThatThrownBy(() -> service.storeOrReuse(doc()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("存储损坏");
        assertThat(storage.putCount()).isZero();
        ContentAsset row = repository.findBySha256(SHA).orElseThrow();
        assertThat(row.getStorageState()).isEqualTo(ContentAssetState.PENDING);
    }

    @Test
    void declaredLengthMismatchDoesNotInsertRow() {
        UploadedDocument lying = new UploadedDocument("a.docx", "text/plain", BYTES.length + 3, doc()::openStream);

        assertThatThrownBy(() -> service.storeOrReuse(lying))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("长度");
        assertThat(repository.findBySha256(SHA)).isEmpty();
        assertThat(storage.putCount()).isZero();
    }

    @Test
    void unexpiredPendingSecondCallerWaitsThenReusesSinglePut() throws Exception {
        waiter.holdUntilReleased();
        storage.blockNextPut();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ContentAsset> first = executor.submit(() -> service.storeOrReuse(doc()));
            assertThat(storage.awaitBlockedPut()).isTrue();
            Future<ContentAsset> second = executor.submit(() -> service.storeOrReuse(doc()));
            assertThat(waiter.awaitWaiting()).isTrue();
            storage.releasePut();
            ContentAsset owner = first.get();
            waiter.release();
            ContentAsset waited = second.get();
            assertThat(waited.getId()).isEqualTo(owner.getId());
            assertThat(storage.putCount()).isOne();
            assertThat(repository.count()).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiredPendingTakeoverDoesNotInsertSecondRow() throws Exception {
        storage.blockNextPut();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ContentAsset> first = executor.submit(() -> service.storeOrReuse(doc()));
            assertThat(storage.awaitBlockedPut()).isTrue();
            clock.advance(Duration.ofMinutes(6));
            ContentAsset takeover = service.storeOrReuse(doc());
            storage.releasePut();
            assertThatThrownBy(first::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("迟到的上传所有者不能完成");
            assertThat(takeover.getStorageState()).isEqualTo(ContentAssetState.READY);
            assertThat(repository.count()).isOne();
            assertThat(storage.size()).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void deleteFailedIntactRecoversReadyWithoutPut() {
        repository.save(ContentAsset.deleteFailed(SHA, InMemoryObjectStorage.BUCKET, KEY, BYTES.length, "text/plain",
                clock.instant()));
        storage.seed(KEY, BYTES, Map.of("sha256", SHA));

        ContentAsset recovered = service.storeOrReuse(doc());

        assertThat(recovered.getStorageState()).isEqualTo(ContentAssetState.READY);
        assertThat(storage.putCount()).isZero();
    }

    @Test
    void deleteFailedMissingObjectIsRewritten() {
        repository.save(ContentAsset.deleteFailed(SHA, InMemoryObjectStorage.BUCKET, KEY, BYTES.length, "text/plain",
                clock.instant()));

        ContentAsset recovered = service.storeOrReuse(doc());

        assertThat(recovered.getStorageState()).isEqualTo(ContentAssetState.READY);
        assertThat(storage.putCount()).isOne();
        assertThat(storage.bytes(KEY)).containsExactly(BYTES);
    }

    @Test
    void deleteFailedDamagedObjectFailsClosed() {
        ContentAsset failed = repository.save(ContentAsset.deleteFailed(SHA, InMemoryObjectStorage.BUCKET, KEY,
                BYTES.length, "text/plain", clock.instant()));
        storage.seed(KEY, "tampered".getBytes(), Map.of("sha256", "2".repeat(64)));

        assertThatThrownBy(() -> service.storeOrReuse(doc()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("存储损坏");
        assertThat(repository.findById(failed.getId()).orElseThrow().getStorageState())
                .isEqualTo(ContentAssetState.DELETE_FAILED);
        assertThat(storage.putCount()).isZero();
    }

    @Test
    void requireReadyTouchesReadyAndRejectsDeleteStates() {
        ContentAsset ready = repository.save(ContentAsset.stored(SHA, InMemoryObjectStorage.BUCKET, KEY, BYTES.length,
                "text/plain", Instant.parse("2026-08-01T00:00:00Z")));
        jdbcTemplate.update("update rag_content_asset set unreferenced_since = ? where id = ?",
                Instant.parse("2026-08-20T00:00:00Z"), ready.getId());

        ContentAsset touched = service.requireReadyForReference(ready.getId());
        assertThat(columnInstant(ready.getId(), "unreferenced_since")).isNull();
        assertThat(touched.getLastTouchedAt()).isEqualTo(clock.instant());

        ContentAsset deleting = repository.save(ContentAsset.deletePending("a".repeat(64), InMemoryObjectStorage.BUCKET,
                "other", 1, "text/plain", clock.instant()));
        assertThatThrownBy(() -> service.requireReadyForReference(deleting.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能建立引用");

        ContentAsset failed = repository.save(ContentAsset.deleteFailed("b".repeat(64), InMemoryObjectStorage.BUCKET,
                "failed", 1, "text/plain", clock.instant()));
        assertThatThrownBy(() -> service.requireReadyForReference(failed.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能建立引用");
    }

    @Test
    void unexpiredPendingWithoutWaiterTimesOutBusy() {
        repository.save(ContentAsset.pending(SHA, InMemoryObjectStorage.BUCKET, KEY, BYTES.length, "text/plain",
                "owner-1", clock.instant().plus(Duration.ofMinutes(4)), clock.instant()));

        assertThatThrownBy(() -> service.storeOrReuse(doc()))
                .isInstanceOf(ContentAssetBusyException.class)
                .hasMessageContaining("正在写入");
        assertThat(storage.putCount()).isZero();
    }

    private UploadedDocument doc() {
        return UploadedDocument.of("制度.docx", "text/plain", BYTES);
    }

    private Instant columnInstant(Long id, String column) {
        Object value = jdbcTemplate.queryForObject(
                "select " + column + " from rag_content_asset where id = ?", Object.class, id);
        return toInstant(value);
    }

    static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        throw new IllegalStateException("无法解析时间列: " + value.getClass());
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
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

        @Bean
        @Primary
        ControllablePendingWaiter controllablePendingWaiter() {
            return new ControllablePendingWaiter();
        }
    }
}
