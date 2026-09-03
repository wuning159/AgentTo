package com.agentto.rag.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryObjectStorage implements ObjectStorageService {

    public static final String BUCKET = "test-bucket";

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> metadata = new ConcurrentHashMap<>();
    private final AtomicInteger putCount = new AtomicInteger();
    private final AtomicInteger statCount = new AtomicInteger();
    private final AtomicInteger getCount = new AtomicInteger();
    private final AtomicInteger deleteCount = new AtomicInteger();
    private final AtomicBoolean blockNextPut = new AtomicBoolean();
    private volatile CountDownLatch putEntered = new CountDownLatch(1);
    private volatile CountDownLatch putGate = new CountDownLatch(0);
    private volatile RuntimeException deleteFailure;

    @Override
    public StoredObject put(String objectKey, byte[] content, String contentType) {
        return put(objectKey, new ByteArrayInputStream(content), content.length, contentType, Map.of());
    }

    @Override
    public StoredObject put(String objectKey, InputStream content, long contentLength, String contentType,
            Map<String, String> metadata) {
        if (blockNextPut.compareAndSet(true, false)) {
            putEntered.countDown();
            awaitGate();
        }
        try {
            byte[] bytes = content.readNBytes(toInt(contentLength));
            objects.put(objectKey, bytes);
            this.metadata.put(objectKey, Map.copyOf(metadata == null ? Map.of() : metadata));
            putCount.incrementAndGet();
            return new StoredObject(BUCKET, objectKey);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public InputStream get(String bucket, String objectKey) {
        getCount.incrementAndGet();
        byte[] bytes = objects.get(objectKey);
        if (bytes == null) {
            throw new IllegalStateException("对象不存在");
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public StoredObject expectedLocation(String objectKey) {
        return new StoredObject(BUCKET, objectKey);
    }

    @Override
    public Optional<ObjectStat> stat(String bucket, String objectKey) {
        statCount.incrementAndGet();
        byte[] bytes = objects.get(objectKey);
        if (bytes == null) {
            return Optional.empty();
        }
        return Optional.of(new ObjectStat(bucket, objectKey, bytes.length,
                metadata.getOrDefault(objectKey, Map.of())));
    }

    @Override
    public void delete(String bucket, String objectKey) {
        deleteCount.incrementAndGet();
        if (deleteFailure != null) {
            throw deleteFailure;
        }
        objects.remove(objectKey);
        metadata.remove(objectKey);
    }

    @Override
    public boolean healthy() {
        return true;
    }

    public void blockNextPut() {
        putEntered = new CountDownLatch(1);
        putGate = new CountDownLatch(1);
        blockNextPut.set(true);
    }

    public boolean awaitBlockedPut() throws InterruptedException {
        return putEntered.await(5, TimeUnit.SECONDS);
    }

    public void releasePut() {
        putGate.countDown();
    }

    public void failNextDeletes(RuntimeException exception) {
        this.deleteFailure = exception;
    }

    public void seed(String objectKey, byte[] content, Map<String, String> userMetadata) {
        objects.put(objectKey, content.clone());
        metadata.put(objectKey, Map.copyOf(userMetadata == null ? Map.of() : userMetadata));
    }

    public void seedWithoutMetadata(String objectKey, byte[] content) {
        objects.put(objectKey, content.clone());
        metadata.put(objectKey, Map.of());
    }

    public void remove(String objectKey) {
        objects.remove(objectKey);
        metadata.remove(objectKey);
    }

    public byte[] bytes(String key) {
        return objects.get(key);
    }

    public int size() {
        return objects.size();
    }

    public int putCount() {
        return putCount.get();
    }

    public int statCount() {
        return statCount.get();
    }

    public int getCount() {
        return getCount.get();
    }

    public int deleteCount() {
        return deleteCount.get();
    }

    public void clear() {
        objects.clear();
        metadata.clear();
        putCount.set(0);
        statCount.set(0);
        getCount.set(0);
        deleteCount.set(0);
        deleteFailure = null;
        blockNextPut.set(false);
        putEntered = new CountDownLatch(1);
        putGate = new CountDownLatch(0);
    }

    private void awaitGate() {
        try {
            if (!putGate.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("测试等待上传放行超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("测试线程被中断", exception);
        }
    }

    private static int toInt(long contentLength) {
        if (contentLength > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("测试存储不支持超大对象");
        }
        return (int) contentLength;
    }
}
