package com.agentto.rag.storage;

import java.io.InputStream;

public interface ObjectStorageService {

    StoredObject put(String objectKey, byte[] content, String contentType);

    InputStream get(String bucket, String objectKey);

    default void clearAll() { throw new UnsupportedOperationException("当前对象存储不支持清理"); }

    boolean healthy();
}
