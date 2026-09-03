package com.agentto.rag.storage;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record ObjectStat(String bucket, String objectKey, long size, Map<String, String> metadata) {

    public ObjectStat {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Optional<String> sha256() {
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if ("sha256".equals(key) || "x-amz-meta-sha256".equals(key)) {
                return Optional.ofNullable(entry.getValue());
            }
        }
        return Optional.empty();
    }
}
