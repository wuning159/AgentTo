package com.agentto.rag.retrieval;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.agentto.rag.common.api.BusinessException;

@Service
public class RetrievalJobService {

    private static final Duration RETENTION = Duration.ofMinutes(30);
    private static final int MAX_JOBS = 500;

    private final HybridRetrievalService retrievalService;
    private final Executor executor;
    private final Clock clock;
    private final Map<String, RetrievalJobState> jobs = new ConcurrentHashMap<>();

    @Autowired
    public RetrievalJobService(HybridRetrievalService retrievalService,
            @Qualifier("retrievalJobExecutor") Executor executor) {
        this(retrievalService, executor, Clock.systemUTC());
    }

    RetrievalJobService(HybridRetrievalService retrievalService, Executor executor, Clock clock) {
        this.retrievalService = retrievalService;
        this.executor = executor;
        this.clock = clock;
    }

    public String create(RetrievalRequest request) {
        cleanup();
        if (jobs.size() >= MAX_JOBS) {
            throw new BusinessException("RETRIEVAL_JOB_CAPACITY", "检索任务过多，请稍后重试",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        String jobUid = UUID.randomUUID().toString().replace("-", "");
        RetrievalJobState state = new RetrievalJobState(jobUid, clock);
        jobs.put(jobUid, state);
        try {
            executor.execute(() -> execute(request, state));
        } catch (RuntimeException exception) {
            jobs.remove(jobUid);
            throw exception;
        }
        return jobUid;
    }

    public RetrievalJobSnapshot get(String jobUid) {
        cleanup();
        RetrievalJobState state = jobs.get(jobUid);
        if (state == null) {
            throw new BusinessException("RETRIEVAL_JOB_NOT_FOUND", "检索任务不存在或已过期", HttpStatus.NOT_FOUND);
        }
        return state.snapshot();
    }

    private void execute(RetrievalRequest request, RetrievalJobState state) {
        try {
            state.complete(retrievalService.search(request, state));
        } catch (RuntimeException exception) {
            state.fail(exception);
        }
    }

    private void cleanup() {
        Instant threshold = clock.instant().minus(RETENTION);
        jobs.entrySet().removeIf(entry -> entry.getValue().expired(threshold));
        while (jobs.size() >= MAX_JOBS) {
            String oldestTerminal = jobs.values().stream()
                    .map(RetrievalJobState::snapshot)
                    .filter(snapshot -> snapshot.completedAt() != null)
                    .min(Comparator.comparing(RetrievalJobSnapshot::completedAt))
                    .map(RetrievalJobSnapshot::jobUid)
                    .orElse(null);
            if (oldestTerminal == null) return;
            jobs.remove(oldestTerminal);
        }
    }
}
