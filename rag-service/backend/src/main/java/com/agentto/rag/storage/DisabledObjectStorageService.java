package com.agentto.rag.storage;

import java.io.InputStream;

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
    public InputStream get(String bucket, String objectKey) {
        throw new IllegalStateException("对象存储未启用");
    }

    @Override
    public void clearAll() {
    }

    @Override
    public boolean healthy() {
        return false;
    }
}
