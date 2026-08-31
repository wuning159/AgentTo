package com.agentto.rag.asset;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentto.rag.storage.ObjectStat;
import com.agentto.rag.storage.ObjectStorageService;
import com.agentto.rag.storage.StoredObject;

@Service
public class ContentAssetService {

    private static final int BUFFER_SIZE = 8192;

    private final ContentAssetRepository repository;
    private final ObjectStorageService storage;
    private final Clock clock;
    private final ContentAssetPendingWaiter waiter;
    private final ContentAssetLifecycleProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final ContentAssetMetrics metrics;

    public ContentAssetService(ContentAssetRepository repository, ObjectStorageService storage, Clock clock,
            ContentAssetPendingWaiter waiter, ContentAssetLifecycleProperties properties,
            TransactionTemplate transactionTemplate, ContentAssetMetrics metrics) {
        this.repository = repository;
        this.storage = storage;
        this.clock = clock;
        this.waiter = waiter;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    public ContentAsset storeOrReuse(UploadedDocument document, String ignoredObjectKey) {
        return storeOrReuse(document);
    }

    public ContentAsset storeOrReuse(UploadedDocument document) {
        ContentDigest digest = digest(document);
        UploadClaim claim = claimUpload(digest.sha256(), digest.byteCount(), document.contentType());
        if (claim.reused()) {
            verifyStoredObject(claim.asset());
            return markReadyReused(claim.asset().getId());
        }
        if (claim.waiting()) {
            waiter.awaitReady(digest.sha256());
            ContentAsset ready = repository.findBySha256(digest.sha256())
                    .filter(ContentAsset::isReady)
                    .orElseThrow(() -> new ContentAssetBusyException("内容资产正在写入"));
            verifyStoredObject(ready);
            return markReadyReused(ready.getId());
        }
        try {
            if (claim.asset().getStorageState() == ContentAssetState.DELETE_FAILED) {
                recoverDeleteFailed(claim.asset(), document, digest);
            } else {
                publishCanonicalObject(document, digest);
            }
            return completeReady(claim);
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    public ContentAsset requireReadyForReference(Long assetId) {
        ContentAsset current = repository.findById(assetId).orElseThrow(() -> new IllegalStateException("内容资产不存在"));
        if (current.isPending()) {
            waiter.awaitReady(current.getSha256());
        }
        return transactionTemplate.execute(status -> {
            ContentAsset locked = repository.findByIdForUpdate(assetId)
                    .orElseThrow(() -> new IllegalStateException("内容资产不存在"));
            if (locked.isReady()) {
                locked.markReused(clock.instant());
                return repository.save(locked);
            }
            throw new IllegalStateException("不能建立引用");
        });
    }

    private UploadClaim claimUpload(String sha256, long length, String contentType) {
        try {
            return transactionTemplate.execute(status -> doClaim(sha256, length, contentType));
        } catch (DataIntegrityViolationException exception) {
            return transactionTemplate.execute(status -> recoverUniqueViolation(sha256));
        }
    }

    private UploadClaim doClaim(String sha256, long length, String contentType) {
        Instant now = clock.instant();
        Optional<ContentAsset> existing = repository.findBySha256ForUpdate(sha256);
        if (existing.isEmpty()) {
            return insertPending(sha256, length, contentType, now);
        }
        ContentAsset asset = existing.get();
        return switch (asset.getStorageState()) {
            case READY -> UploadClaim.reused(asset);
            case PENDING -> claimPending(asset, now);
            case DELETE_FAILED -> claimDeleteFailed(asset, now);
            case DELETE_PENDING -> throw new ContentAssetBusyException("内容资产不能建立引用");
        };
    }

    private UploadClaim insertPending(String sha256, long length, String contentType, Instant now) {
        String owner = newOwner();
        StoredObject location = storage.expectedLocation(CanonicalObjectKey.of(sha256));
        ContentAsset pending = ContentAsset.pending(sha256, location.bucket(), location.objectKey(), length,
                contentType, owner, now.plus(properties.pendingLease()), now);
        return UploadClaim.owner(repository.saveAndFlush(pending), owner);
    }

    private UploadClaim claimPending(ContentAsset asset, Instant now) {
        if (asset.leaseValid(now)) {
            return UploadClaim.pendingWait(asset);
        }
        String owner = newOwner();
        asset.takeOverExpiredLease(owner, now.plus(properties.pendingLease()), now);
        metrics.incrementLeaseTakeovers();
        return UploadClaim.owner(repository.save(asset), owner);
    }

    private UploadClaim claimDeleteFailed(ContentAsset asset, Instant now) {
        if (asset.leaseValid(now)) {
            throw new ContentAssetBusyException("内容资产正在恢复");
        }
        String owner = newOwner();
        asset.claimRecoveryLease(owner, now.plus(properties.pendingLease()), now);
        return UploadClaim.owner(repository.save(asset), owner);
    }

    private UploadClaim recoverUniqueViolation(String sha256) {
        ContentAsset asset = repository.findBySha256ForUpdate(sha256)
                .orElseThrow(() -> new ContentAssetBusyException("内容资产正在写入"));
        if (asset.isReady()) {
            return UploadClaim.reused(asset);
        }
        throw new ContentAssetBusyException("内容资产正在写入");
    }

    private void recoverDeleteFailed(ContentAsset asset, UploadedDocument document, ContentDigest digest) {
        Optional<ObjectStat> existing = storage.stat(asset.getBucket(), asset.getObjectKey());
        if (existing.isPresent()) {
            assertShaMatches(existing.get(), digest.sha256(), asset.getBucket(), asset.getObjectKey());
            return;
        }
        publishCanonicalObject(document, digest);
    }

    private void publishCanonicalObject(UploadedDocument document, ContentDigest digest) {
        StoredObject location = storage.expectedLocation(CanonicalObjectKey.of(digest.sha256()));
        Optional<ObjectStat> before = storage.stat(location.bucket(), location.objectKey());
        if (before.isPresent()) {
            assertShaMatches(before.get(), digest.sha256(), location.bucket(), location.objectKey());
            return;
        }
        try (InputStream stream = document.openStream()) {
            storage.put(location.objectKey(), stream, digest.byteCount(),
                    document.contentType() == null ? "application/octet-stream" : document.contentType(),
                    Map.of("sha256", digest.sha256()));
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取上传文件失败", exception);
        }
        ObjectStat after = storage.stat(location.bucket(), location.objectKey())
                .orElseThrow(() -> new IllegalStateException("存储损坏：上传后对象不存在"));
        assertShaMatches(after, digest.sha256(), location.bucket(), location.objectKey());
    }

    private void verifyStoredObject(ContentAsset asset) {
        ObjectStat stat = storage.stat(asset.getBucket(), asset.getObjectKey())
                .orElseThrow(() -> new IllegalStateException("存储损坏：对象不存在"));
        assertShaMatches(stat, asset.getSha256(), asset.getBucket(), asset.getObjectKey());
    }

    private void assertShaMatches(ObjectStat stat, String expectedSha, String bucket, String objectKey) {
        Optional<String> metadataSha = stat.sha256();
        if (metadataSha.isPresent()) {
            if (!expectedSha.equalsIgnoreCase(metadataSha.get())) {
                throw new IllegalStateException("存储损坏：对象校验和与登记不一致");
            }
            return;
        }
        try (InputStream stream = storage.get(bucket, objectKey)) {
            String actual = sha256(stream);
            if (!expectedSha.equalsIgnoreCase(actual)) {
                throw new IllegalStateException("存储损坏：对象校验和与登记不一致");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("存储损坏：无法读取对象", exception);
        }
    }

    private ContentAsset completeReady(UploadClaim claim) {
        return transactionTemplate.execute(status -> {
            ContentAsset locked = repository.findByIdForUpdate(claim.asset().getId())
                    .orElseThrow(() -> new IllegalStateException("内容资产不存在"));
            locked.markReady(claim.owner(), clock.instant());
            return repository.save(locked);
        });
    }

    private ContentAsset markReadyReused(Long id) {
        return transactionTemplate.execute(status -> {
            ContentAsset locked = repository.findByIdForUpdate(id)
                    .orElseThrow(() -> new IllegalStateException("内容资产不存在"));
            if (!locked.isReady()) {
                throw new ContentAssetBusyException("内容资产正在写入");
            }
            locked.markReused(clock.instant());
            return repository.save(locked);
        });
    }

    private ContentDigest digest(UploadedDocument document) {
        long count = 0;
        MessageDigest digest = sha256Digest();
        try (InputStream stream = document.openStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                    count += read;
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取上传文件失败", exception);
        }
        if (count != document.contentLength()) {
            throw new IllegalArgumentException("上传内容长度与声明不一致");
        }
        return new ContentDigest(HexFormat.of().formatHex(digest.digest()), count);
    }

    private String sha256(InputStream stream) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = stream.read(buffer)) >= 0) {
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

    private static String newOwner() {
        return UUID.randomUUID().toString();
    }

    private record ContentDigest(String sha256, long byteCount) {
    }
}
