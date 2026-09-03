package com.agentto.rag.storage;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "rag.storage", name = "enabled", havingValue = "false")
public class DisabledObjectStorageService implements ObjectStorageService {

    @Override
    public StoredObject put(String objectKey, byte[] content, String contentType) {
        throw new IllegalStateException("对象存储未启用");
    }

    @Override
    public StoredObject put(String objectKey, InputStream content, long contentLength, String contentType,
            Map<String, String> metadata) {
        throw new IllegalStateException("对象存储未启用");
    }

    @Override
    public InputStream get(String bucket, String objectKey) {
        throw new IllegalStateException("对象存储未启用");
    }

    @Override
    public StoredObject expectedLocation(String objectKey) {
        return new StoredObject("disabled", objectKey);
    }

    @Override
    public Optional<ObjectStat> stat(String bucket, String objectKey) {
        return Optional.empty();
    }

    @Override
    public void delete(String bucket, String objectKey) {
    }

    @Override
    public void clearAll() {
    }

    @Override
    public boolean healthy() {
        return false;
    }
}
