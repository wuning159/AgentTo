package com.agentto.rag.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;

@Service
@ConditionalOnProperty(prefix = "rag.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient client;
    private final String bucket;
    private final Set<String> readyBuckets = ConcurrentHashMap.newKeySet();

    public MinioObjectStorageService(StorageProperties properties) {
        require(properties.endpoint(), "rag.storage.endpoint");
        require(properties.accessKey(), "rag.storage.access-key");
        require(properties.secretKey(), "rag.storage.secret-key");
        this.bucket = require(properties.bucket(), "rag.storage.bucket");
        this.client = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Override
    public StoredObject put(String objectKey, byte[] content, String contentType) {
        return put(objectKey, new ByteArrayInputStream(content), content.length, contentType, Map.of());
    }

    @Override
    public StoredObject put(String objectKey, InputStream content, long contentLength, String contentType,
            Map<String, String> metadata) {
        try {
            ensureBucket(bucket);
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(content, contentLength, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .userMetadata(metadata == null ? Map.of() : metadata)
                    .build());
            return new StoredObject(bucket, objectKey);
        } catch (Exception exception) {
            throw new IllegalStateException("MinIO 文件上传失败", exception);
        }
    }

    @Override
    public InputStream get(String bucket, String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new IllegalStateException("MinIO 文件读取失败", exception);
        }
    }

    @Override
    public StoredObject expectedLocation(String objectKey) {
        return new StoredObject(bucket, objectKey);
    }

    @Override
    public Optional<ObjectStat> stat(String bucket, String objectKey) {
        try {
            StatObjectResponse response = client.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return Optional.of(new ObjectStat(bucket, objectKey, response.size(), response.userMetadata()));
        } catch (ErrorResponseException exception) {
            String code = exception.errorResponse() == null ? "" : exception.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NoSuchBucket".equals(code)) {
                return Optional.empty();
            }
            throw new IllegalStateException("MinIO 对象检查失败", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("MinIO 对象检查失败", exception);
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new IllegalStateException("MinIO 文件删除失败", exception);
        }
    }

    @Override
    public void clearAll() {
        try {
            ensureBucket(bucket);
            for (var result : client.listObjects(ListObjectsArgs.builder().bucket(bucket).recursive(true).build())) {
                client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(result.get().objectName()).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("清空 RAG 文件失败", exception);
        }
    }

    @Override
    public boolean healthy() {
        try {
            ensureBucket(bucket);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void ensureBucket(String name) throws Exception {
        if (readyBuckets.contains(name)) {
            return;
        }
        synchronized (readyBuckets) {
            if (!readyBuckets.contains(name)) {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(name).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(name).build());
                }
                readyBuckets.add(name);
            }
        }
    }

    private String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少配置：" + name);
        }
        return value;
    }
}
