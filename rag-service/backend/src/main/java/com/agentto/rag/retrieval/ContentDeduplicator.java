package com.agentto.rag.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ContentDeduplicator {

    public DedupeResult dedupe(List<RetrievalCandidate> ranked, int limit) {
        Map<String, RetrievalCandidate> representativeByHash = new LinkedHashMap<>();
        List<RetrievalCandidate> trace = new ArrayList<>();
        List<RetrievalCandidate> selected = new ArrayList<>();
        int duplicateCount = 0;
        for (RetrievalCandidate candidate : ranked) {
            String hash = ContentFingerprint.sha256(candidate.content());
            RetrievalCandidate representative = representativeByHash.get(hash);
            if (representative == null) {
                RetrievalCandidate kept = candidate.withDedupe(hash, DedupeStatus.KEPT, null);
                representativeByHash.put(hash, kept);
                trace.add(kept);
                if (selected.size() < Math.max(limit, 0)) {
                    selected.add(kept);
                }
            } else {
                trace.add(candidate.withDedupe(hash, DedupeStatus.DUPLICATE, representative.chunkId()));
                duplicateCount++;
            }
        }
        return new DedupeResult(List.copyOf(selected), List.copyOf(trace), duplicateCount);
    }
}
