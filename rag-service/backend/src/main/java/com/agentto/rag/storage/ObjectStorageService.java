package com.agentto.rag.storage;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

public interface ObjectStorageService {

    StoredObject put(String objectKey, byte[] content, String contentType);

    StoredObject put(String objectKey, InputStream content, long contentLength, String contentType,
            Map<String, String> metadata);

    InputStream get(String bucket, String objectKey);

    StoredObject expectedLocation(String objectKey);

    Optional<ObjectStat> stat(String bucket, String objectKey);

    void delete(String bucket, String objectKey);

    default void clearAll() {
        throw new UnsupportedOperationException("当前对象存储不支持清理");
    }

    boolean healthy();
}
