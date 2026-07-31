package com.agentto.rag.retrieval;

import java.util.List;

public record RetrievalResponse(
        String traceUid,
        List<RetrievalCandidate> candidates,
        String fallbackReason,
        RetrievalTimings timings) {
}
