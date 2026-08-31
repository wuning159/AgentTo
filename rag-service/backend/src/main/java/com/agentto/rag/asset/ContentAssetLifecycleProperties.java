package com.agentto.rag.asset;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rag.asset.lifecycle")
public record ContentAssetLifecycleProperties(
        Duration unreferencedTtl,
        Duration pendingLease,
        Duration cleanupInterval,
        Duration workerInterval,
        Duration pendingWaitTimeout,
        Duration pendingWaitPoll) {

    public ContentAssetLifecycleProperties {
        unreferencedTtl = requirePositive(unreferencedTtl, Duration.ofHours(168), "unreferenced-ttl");
        pendingLease = requirePositive(pendingLease, Duration.ofMinutes(5), "pending-lease");
        cleanupInterval = requirePositive(cleanupInterval, Duration.ofMinutes(1), "cleanup-interval");
        workerInterval = requirePositive(workerInterval, Duration.ofMinutes(1), "worker-interval");
        pendingWaitTimeout = requirePositive(pendingWaitTimeout, Duration.ofSeconds(30), "pending-wait-timeout");
        pendingWaitPoll = requirePositive(pendingWaitPoll, Duration.ofMillis(200), "pending-wait-poll");
    }

    private static Duration requirePositive(Duration value, Duration fallback, String name) {
        Duration resolved = value == null ? fallback : value;
        if (resolved.isZero() || resolved.isNegative()) {
            throw new IllegalArgumentException("rag.asset.lifecycle." + name + " 必须为正");
        }
        return resolved;
    }
}
