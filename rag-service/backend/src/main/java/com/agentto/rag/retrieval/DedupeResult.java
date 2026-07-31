package com.agentto.rag.retrieval;

import java.util.List;

public record DedupeResult(
        List<RetrievalCandidate> selected,
        List<RetrievalCandidate> traceCandidates,
        int duplicateCount) {
}
