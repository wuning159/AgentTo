package com.agentto.rag.retrieval;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;

final class RetrievalJobState implements RetrievalProgressReporter {

    private final String jobUid;
    private final Clock clock;
    private final Instant createdAt;
    private final EnumMap<RetrievalStage, RetrievalStageSnapshot> stages = new EnumMap<>(RetrievalStage.class);

    private RetrievalJobStatus status = RetrievalJobStatus.QUEUED;
    private RetrievalStage currentStage;
    private RetrievalResponse result;
    private String error;
    private Instant completedAt;

    RetrievalJobState(String jobUid, Clock clock) {
        this.jobUid = jobUid;
        this.clock = clock;
        this.createdAt = clock.instant();
        for (RetrievalStage stage : RetrievalStage.values()) {
            stages.put(stage, new RetrievalStageSnapshot(stage, RetrievalStageStatus.PENDING,
                    null, null, null));
        }
    }

    @Override
    public synchronized void running(RetrievalStage stage) {
        if (terminal()) return;
        status = RetrievalJobStatus.RUNNING;
        currentStage = stage;
        stages.put(stage, new RetrievalStageSnapshot(stage, RetrievalStageStatus.RUNNING,
                null, null, null));
    }

    @Override
    public synchronized void completed(RetrievalStage stage, long elapsedMs, Integer itemCount) {
        if (terminal()) return;
        currentStage = stage;
        stages.put(stage, new RetrievalStageSnapshot(stage, RetrievalStageStatus.COMPLETED,
                elapsedMs, itemCount, null));
    }

    @Override
    public synchronized void degraded(RetrievalStage stage, long elapsedMs, String message) {
        if (terminal()) return;
        currentStage = stage;
        stages.put(stage, new RetrievalStageSnapshot(stage, RetrievalStageStatus.DEGRADED,
                elapsedMs, null, message));
    }

    @Override
    public synchronized void skipped(RetrievalStage stage, String message) {
        if (terminal()) return;
        stages.put(stage, new RetrievalStageSnapshot(stage, RetrievalStageStatus.SKIPPED,
                null, null, message));
    }

    @Override
    public synchronized void failed(RetrievalStage stage, String message) {
        if (terminal()) return;
        currentStage = stage;
        status = RetrievalJobStatus.FAILED;
        error = message;
        completedAt = clock.instant();
        stages.put(stage, new RetrievalStageSnapshot(stage, RetrievalStageStatus.FAILED,
                null, null, message));
    }

    synchronized void complete(RetrievalResponse response) {
        if (status == RetrievalJobStatus.FAILED) return;
        result = response;
        status = RetrievalJobStatus.COMPLETED;
        completedAt = clock.instant();
    }

    synchronized void fail(RuntimeException exception) {
        if (status == RetrievalJobStatus.FAILED) return;
        String message = exception.getMessage();
        error = message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
        status = RetrievalJobStatus.FAILED;
        completedAt = clock.instant();
        if (currentStage != null) {
            RetrievalStageSnapshot current = stages.get(currentStage);
            if (current.status() == RetrievalStageStatus.RUNNING) {
                stages.put(currentStage, new RetrievalStageSnapshot(currentStage, RetrievalStageStatus.FAILED,
                        null, null, error));
            }
        }
    }

    synchronized RetrievalJobSnapshot snapshot() {
        List<RetrievalStageSnapshot> snapshots = List.of(RetrievalStage.values()).stream()
                .map(stages::get)
                .toList();
        return new RetrievalJobSnapshot(jobUid, status, currentStage, snapshots, result, error,
                createdAt, completedAt);
    }

    synchronized boolean expired(Instant threshold) {
        return completedAt != null && completedAt.isBefore(threshold);
    }

    private boolean terminal() {
        return status == RetrievalJobStatus.COMPLETED || status == RetrievalJobStatus.FAILED;
    }
}
