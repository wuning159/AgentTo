package com.agentto.rag.asset;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentto.rag.storage.ObjectStorageService;

@Service
public class ObjectCleanupTaskWorker {

    private final ObjectCleanupTaskRepository taskRepository;
    private final ContentAssetRepository assetRepository;
    private final ObjectStorageService storage;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ObjectCleanupTaskWorker(ObjectCleanupTaskRepository taskRepository, ContentAssetRepository assetRepository,
            ObjectStorageService storage, Clock clock, TransactionTemplate transactionTemplate) {
        this.taskRepository = taskRepository;
        this.assetRepository = assetRepository;
        this.storage = storage;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    public void runOnce() {
        Instant now = clock.instant();
        List<ObjectCleanupTask> due = taskRepository.findDue(now);
        for (ObjectCleanupTask candidate : due) {
            transactionTemplate.executeWithoutResult(status -> {
                ObjectCleanupTask task = taskRepository.findById(candidate.getId()).orElse(null);
                if (task == null) {
                    return;
                }
                if (assetRepository.existsByBucketAndObjectKey(task.getBucket(), task.getObjectKey())) {
                    task.markSkipped(clock.instant());
                    taskRepository.save(task);
                    return;
                }
                try {
                    storage.delete(task.getBucket(), task.getObjectKey());
                    task.markComplete(clock.instant());
                    taskRepository.save(task);
                } catch (RuntimeException exception) {
                    task.markRetryableFailure(clock.instant(), Duration.ofMinutes(5), "OBJECT_DELETE_FAILED");
                    taskRepository.save(task);
                }
            });
        }
    }
}
