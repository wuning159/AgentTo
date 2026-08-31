package com.agentto.rag.asset;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "rag.scheduling", name = "enabled", havingValue = "true")
public class ContentAssetSchedulingConfiguration {

    private final ContentAssetCleanupJob cleanupJob;
    private final ObjectCleanupTaskWorker worker;

    ContentAssetSchedulingConfiguration(ContentAssetCleanupJob cleanupJob, ObjectCleanupTaskWorker worker) {
        this.cleanupJob = cleanupJob;
        this.worker = worker;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        cleanupJob.runOnce();
    }

    @Scheduled(fixedDelay = 60_000)
    public void sweepOrphans() {
        worker.runOnce();
    }
}
