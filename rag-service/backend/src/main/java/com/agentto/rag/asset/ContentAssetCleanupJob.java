package com.agentto.rag.asset;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentto.rag.storage.ObjectStat;
import com.agentto.rag.storage.ObjectStorageService;

@Service
public class ContentAssetCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ContentAssetCleanupJob.class);
    private static final int SHA_BUFFER_SIZE = 8192;

    private static final String DELETE_FAILED_CODE = "OBJECT_DELETE_FAILED";

    private final ContentAssetRepository repository;
    private final ContentAssetReferenceProbe referenceProbe;
    private final ObjectStorageService storage;
    private final Clock clock;
    private final ContentAssetLifecycleProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final ContentAssetMetrics metrics;

    public ContentAssetCleanupJob(ContentAssetRepository repository, ContentAssetReferenceProbe referenceProbe,
            ObjectStorageService storage, Clock clock, ContentAssetLifecycleProperties properties,
            TransactionTemplate transactionTemplate, ContentAssetMetrics metrics) {
        this.repository = repository;
        this.referenceProbe = referenceProbe;
        this.storage = storage;
        this.clock = clock;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    public void runOnce() {
        Instant now = clock.instant();
        recoverExpiredPending(now);
        markUnreferencedAndDueDeletes(now);
        processDueDeletes(now);
    }

    private void recoverExpiredPending(Instant now) {
        List<ContentAsset> expired = repository.findExpiredPending(now);
        for (ContentAsset candidate : expired) {
            transactionTemplate.executeWithoutResult(status -> {
                ContentAsset asset = repository.findByIdForUpdate(candidate.getId()).orElse(null);
                if (asset == null || !asset.isPending() || asset.leaseValid(clock.instant())) {
                    return;
                }
                Optional<ObjectStat> stat = storage.stat(asset.getBucket(), asset.getObjectKey());
                if (stat.isPresent() && objectMatches(stat.get(), asset)) {
                    asset.recoverReadyFromValidObject(clock.instant());
                    repository.save(asset);
                    metrics.incrementImmediateCompensation();
                    return;
                }
                asset.releaseExpiredLease(clock.instant());
                repository.save(asset);
            });
        }
    }

    private void markUnreferencedAndDueDeletes(Instant now) {
        List<ContentAsset> ready = repository.findByStorageStateOrderByIdAsc(ContentAssetState.READY);
        Instant cutoff = now.minus(properties.unreferencedTtl());
        for (ContentAsset candidate : ready) {
            transactionTemplate.executeWithoutResult(status -> {
                ContentAsset asset = repository.findByIdForUpdate(candidate.getId()).orElse(null);
                if (asset == null || !asset.isReady()) {
                    return;
                }
                if (referenceProbe.isReferenced(asset.getId(), asset.getSha256())) {
                    if (asset.getUnreferencedSince() != null) {
                        asset.restoreReadyFromUnexpectedReference(clock.instant());
                        repository.save(asset);
                        metrics.incrementReferencedProtection();
                    }
                    return;
                }
                if (asset.getUnreferencedSince() == null) {
                    asset.markUnreferenced(clock.instant());
                    repository.save(asset);
                    return;
                }
                if (!asset.getUnreferencedSince().isAfter(cutoff)) {
                    asset.markDeletePending(clock.instant());
                    repository.save(asset);
                    metrics.incrementSevenDayCandidates();
                }
            });
        }
    }

    private void processDueDeletes(Instant now) {
        List<ContentAsset> due = repository.findDueDeleteRetries(now);
        for (ContentAsset candidate : due) {
            transactionTemplate.executeWithoutResult(status -> {
                ContentAsset asset = repository.findByIdForUpdate(candidate.getId()).orElse(null);
                if (asset == null) {
                    return;
                }
                if (asset.getStorageState() != ContentAssetState.DELETE_PENDING
                        && asset.getStorageState() != ContentAssetState.DELETE_FAILED) {
                    return;
                }
                if (referenceProbe.isReferenced(asset.getId(), asset.getSha256())) {
                    asset.restoreReadyFromUnexpectedReference(clock.instant());
                    repository.save(asset);
                    metrics.incrementReferencedProtection();
                    return;
                }
                if (asset.getStorageState() == ContentAssetState.DELETE_FAILED) {
                    metrics.incrementDeleteRetries();
                }
                String owner = UUID.randomUUID().toString();
                asset.claimDeleteLease(owner, clock.instant().plus(properties.pendingLease()), clock.instant());
                repository.save(asset);
                try {
                    storage.delete(asset.getBucket(), asset.getObjectKey());
                    repository.delete(asset);
                } catch (RuntimeException exception) {
                    asset.markDeleteFailed(clock.instant(), backoff(asset.getDeleteAttemptCount()), DELETE_FAILED_CODE);
                    repository.save(asset);
                }
            });
        }
    }

    /**
     * 元数据缺失时必须流式重算 SHA，不得把「无元数据」当成匹配。
     * 读取失败或哈希不一致时返回 false：保持 PENDING、释放租约，不删除共享键。
     */
    private boolean objectMatches(ObjectStat stat, ContentAsset asset) {
        Optional<String> metadataSha = stat.sha256();
        if (metadataSha.isPresent()) {
            return metadataSha.get().equalsIgnoreCase(asset.getSha256());
        }
        try (InputStream in = storage.get(asset.getBucket(), asset.getObjectKey())) {
            return sha256Hex(in).equalsIgnoreCase(asset.getSha256());
        } catch (IOException | RuntimeException e) {
            log.warn("expired pending SHA recompute failed for {}", asset.getId(), e);
            return false;
        }
    }

    private static String sha256Hex(InputStream in) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[SHA_BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            if (read > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private Duration backoff(int attemptCount) {
        int shift = Math.min(Math.max(attemptCount, 0), 6);
        long minutes = Math.min(60L * (1L << shift), 24L * 60L);
        return Duration.ofMinutes(minutes);
    }
}
