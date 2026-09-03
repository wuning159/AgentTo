package com.agentto.rag.asset;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rag_object_cleanup_task")
public class ObjectCleanupTask {

    public static final String PENDING = "PENDING";
    public static final String COMPLETE = "COMPLETE";
    public static final String RETRYABLE_FAILURE = "RETRYABLE_FAILURE";
    public static final String SKIPPED = "SKIPPED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ObjectCleanupTask() {
    }

    public static ObjectCleanupTask pending(String bucket, String objectKey, Instant now) {
        ObjectCleanupTask task = new ObjectCleanupTask();
        task.bucket = bucket;
        task.objectKey = objectKey;
        task.status = PENDING;
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    public void markComplete(Instant now) {
        status = COMPLETE;
        lastErrorCode = null;
        nextRetryAt = null;
        updatedAt = now;
    }

    public void markSkipped(Instant now) {
        status = SKIPPED;
        updatedAt = now;
    }

    public void markRetryableFailure(Instant now, Duration backoff, String errorCode) {
        status = RETRYABLE_FAILURE;
        attemptCount += 1;
        nextRetryAt = now.plus(backoff);
        lastErrorCode = errorCode;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getBucket() {
        return bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }
}
