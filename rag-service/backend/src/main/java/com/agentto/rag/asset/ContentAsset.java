package com.agentto.rag.asset;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rag_content_asset")
public class ContentAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String sha256;

    @Column(nullable = false, length = 128)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_length", nullable = false)
    private long contentLength;

    @Column(name = "content_type")
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_state", nullable = false, length = 32)
    private ContentAssetState storageState;

    @Column(name = "lease_owner", length = 64)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "last_touched_at", nullable = false)
    private Instant lastTouchedAt;

    @Column(name = "unreferenced_since")
    private Instant unreferencedSince;

    @Column(name = "delete_attempt_count", nullable = false)
    private int deleteAttemptCount;

    @Column(name = "next_delete_retry_at")
    private Instant nextDeleteRetryAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContentAsset() {
    }

    public static ContentAsset pending(String sha256, String bucket, String objectKey, long contentLength,
            String contentType, String leaseOwner, Instant leaseExpiresAt, Instant now) {
        ContentAsset asset = new ContentAsset();
        asset.sha256 = sha256;
        asset.bucket = bucket;
        asset.objectKey = objectKey;
        asset.contentLength = contentLength;
        asset.contentType = contentType;
        asset.storageState = ContentAssetState.PENDING;
        asset.leaseOwner = leaseOwner;
        asset.leaseExpiresAt = leaseExpiresAt;
        asset.lastTouchedAt = now;
        asset.createdAt = now;
        asset.updatedAt = now;
        return asset;
    }

    public static ContentAsset stored(String sha256, String bucket, String objectKey, long contentLength,
            String contentType, Instant now) {
        ContentAsset asset = pending(sha256, bucket, objectKey, contentLength, contentType, null, null, now);
        asset.storageState = ContentAssetState.READY;
        return asset;
    }

    public static ContentAsset deleteFailed(String sha256, String bucket, String objectKey, long contentLength,
            String contentType, Instant now) {
        ContentAsset asset = stored(sha256, bucket, objectKey, contentLength, contentType, now);
        asset.storageState = ContentAssetState.DELETE_FAILED;
        return asset;
    }

    public static ContentAsset deletePending(String sha256, String bucket, String objectKey, long contentLength,
            String contentType, Instant now) {
        ContentAsset asset = stored(sha256, bucket, objectKey, contentLength, contentType, now);
        asset.storageState = ContentAssetState.DELETE_PENDING;
        return asset;
    }

    public boolean isReady() {
        return storageState == ContentAssetState.READY;
    }

    public boolean isPending() {
        return storageState == ContentAssetState.PENDING;
    }

    public boolean leaseValid(Instant now) {
        return leaseOwner != null && leaseExpiresAt != null && leaseExpiresAt.isAfter(now);
    }

    public void markReused(Instant now) {
        lastTouchedAt = now;
        unreferencedSince = null;
        updatedAt = now;
    }

    public void takeOverExpiredLease(String owner, Instant expiresAt, Instant now) {
        if (storageState != ContentAssetState.PENDING) {
            throw new ContentAssetBusyException("内容资产正在写入");
        }
        if (leaseValid(now)) {
            throw new ContentAssetBusyException("内容资产正在写入");
        }
        leaseOwner = owner;
        leaseExpiresAt = expiresAt;
        updatedAt = now;
    }

    public void claimRecoveryLease(String owner, Instant expiresAt, Instant now) {
        if (storageState != ContentAssetState.DELETE_FAILED) {
            throw new ContentAssetBusyException("内容资产不能建立引用");
        }
        if (leaseValid(now)) {
            throw new ContentAssetBusyException("内容资产正在恢复");
        }
        leaseOwner = owner;
        leaseExpiresAt = expiresAt;
        updatedAt = now;
    }

    public void markReady(String owner, Instant now) {
        if (owner == null || !owner.equals(leaseOwner)) {
            throw new IllegalStateException("迟到的上传所有者不能完成");
        }
        if (storageState != ContentAssetState.PENDING && storageState != ContentAssetState.DELETE_FAILED) {
            throw new IllegalStateException("迟到的上传所有者不能完成");
        }
        storageState = ContentAssetState.READY;
        leaseOwner = null;
        leaseExpiresAt = null;
        unreferencedSince = null;
        deleteAttemptCount = 0;
        nextDeleteRetryAt = null;
        lastErrorCode = null;
        lastTouchedAt = now;
        updatedAt = now;
    }

    public void recoverReadyFromValidObject(Instant now) {
        if (storageState != ContentAssetState.PENDING) {
            throw new IllegalStateException("只有 PENDING 资产能从有效对象恢复");
        }
        storageState = ContentAssetState.READY;
        leaseOwner = null;
        leaseExpiresAt = null;
        lastTouchedAt = now;
        updatedAt = now;
    }

    public void markUnreferenced(Instant now) {
        if (unreferencedSince == null) {
            unreferencedSince = now;
        }
        updatedAt = now;
    }

    public void markDeletePending(Instant now) {
        storageState = ContentAssetState.DELETE_PENDING;
        updatedAt = now;
    }

    public void claimDeleteLease(String owner, Instant expiresAt, Instant now) {
        leaseOwner = owner;
        leaseExpiresAt = expiresAt;
        updatedAt = now;
    }

    public void markDeleteFailed(Instant now, Duration backoff, String errorCode) {
        storageState = ContentAssetState.DELETE_FAILED;
        deleteAttemptCount += 1;
        nextDeleteRetryAt = now.plus(backoff);
        lastErrorCode = errorCode;
        leaseOwner = null;
        leaseExpiresAt = null;
        updatedAt = now;
    }

    public void releaseExpiredLease(Instant now) {
        leaseOwner = null;
        leaseExpiresAt = null;
        updatedAt = now;
    }

    public void restoreReadyFromUnexpectedReference(Instant now) {
        storageState = ContentAssetState.READY;
        unreferencedSince = null;
        leaseOwner = null;
        leaseExpiresAt = null;
        lastErrorCode = null;
        lastTouchedAt = now;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getSha256() {
        return sha256;
    }

    public String getBucket() {
        return bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public long getContentLength() {
        return contentLength;
    }

    public String getContentType() {
        return contentType;
    }

    public ContentAssetState getStorageState() {
        return storageState;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public Instant getLastTouchedAt() {
        return lastTouchedAt;
    }

    public Instant getUnreferencedSince() {
        return unreferencedSince;
    }

    public int getDeleteAttemptCount() {
        return deleteAttemptCount;
    }

    public Instant getNextDeleteRetryAt() {
        return nextDeleteRetryAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }
}
