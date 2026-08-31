package com.agentto.rag.asset;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentto.rag.storage.ObjectStat;
import com.agentto.rag.storage.ObjectStorageService;

@Service
public class ContentAssetCleanupJob {

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

    private boolean objectMatches(ObjectStat stat, ContentAsset asset) {
        Optional<String> metadataSha = stat.sha256();
        return metadataSha.isEmpty() || metadataSha.get().equalsIgnoreCase(asset.getSha256());
    }

    private Duration backoff(int attemptCount) {
        int shift = Math.min(Math.max(attemptCount, 0), 6);
        long minutes = Math.min(60L * (1L << shift), 24L * 60L);
        return Duration.ofMinutes(minutes);
    }
}
