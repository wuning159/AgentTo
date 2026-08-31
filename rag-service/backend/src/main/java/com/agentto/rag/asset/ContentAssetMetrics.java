package com.agentto.rag.asset;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class ContentAssetMetrics {

    private final Counter leaseTakeovers;
    private final Counter immediateCompensation;
    private final Counter sevenDayCandidates;
    private final Counter deleteRetries;
    private final Counter referencedProtection;

    public ContentAssetMetrics(MeterRegistry registry, ContentAssetRepository repository) {
        this.leaseTakeovers = Counter.builder("rag.asset.lease_takeovers").register(registry);
        this.immediateCompensation = Counter.builder("rag.asset.immediate_compensation").register(registry);
        this.sevenDayCandidates = Counter.builder("rag.asset.seven_day_candidates").register(registry);
        this.deleteRetries = Counter.builder("rag.asset.delete_retries").register(registry);
        this.referencedProtection = Counter.builder("rag.asset.referenced_protection").register(registry);
        Gauge.builder("rag.asset.pending", repository, repo -> repo.countByStorageState(ContentAssetState.PENDING))
                .register(registry);
        Gauge.builder("rag.asset.delete_pending", repository,
                repo -> repo.countByStorageState(ContentAssetState.DELETE_PENDING)).register(registry);
        Gauge.builder("rag.asset.delete_failed", repository,
                repo -> repo.countByStorageState(ContentAssetState.DELETE_FAILED)).register(registry);
    }

    public void incrementLeaseTakeovers() {
        leaseTakeovers.increment();
    }

    public void incrementImmediateCompensation() {
        immediateCompensation.increment();
    }

    public void incrementSevenDayCandidates() {
        sevenDayCandidates.increment();
    }

    public void incrementDeleteRetries() {
        deleteRetries.increment();
    }

    public void incrementReferencedProtection() {
        referencedProtection.increment();
    }
}
