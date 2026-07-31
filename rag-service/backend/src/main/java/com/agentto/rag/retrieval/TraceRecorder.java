package com.agentto.rag.retrieval;

import java.util.List;

public interface TraceRecorder {
    String record(RetrievalRequest request, List<RetrievalCandidate> candidates,
            RetrievalTimings timings, String fallbackReason);

    default String record(RetrievalRequest request, List<RetrievalCandidate> candidates,
            RetrievalTimings timings, String fallbackReason, RetrievalTraceContext context) {
        return record(request, candidates, timings, fallbackReason);
    }
}
