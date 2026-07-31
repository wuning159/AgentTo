package com.agentto.rag.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RrfFusion {

    private final int rankConstant;

    public RrfFusion(int rankConstant) {
        if (rankConstant < 1) {
            throw new IllegalArgumentException("RRF rankConstant 必须大于 0");
        }
        this.rankConstant = rankConstant;
    }

    public int rankConstant() {
        return rankConstant;
    }

    public List<RetrievalCandidate> fuse(List<RetrievalCandidate> keyword,
            List<RetrievalCandidate> vector, int limit) {
        Map<String, RetrievalCandidate> merged = new LinkedHashMap<>();
        addAll(merged, keyword);
        addAll(merged, vector);

        List<Scored> scored = new ArrayList<>();
        for (RetrievalCandidate candidate : merged.values()) {
            double score = reciprocal(candidate.keywordRank()) + reciprocal(candidate.vectorRank());
            scored.add(new Scored(candidate, score));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(item -> item.candidate().chunkId()));

        List<RetrievalCandidate> result = new ArrayList<>();
        int size = Math.min(Math.max(limit, 0), scored.size());
        for (int i = 0; i < size; i++) {
            Scored item = scored.get(i);
            result.add(item.candidate().withRrf(item.score(), i + 1));
        }
        return List.copyOf(result);
    }

    private void addAll(Map<String, RetrievalCandidate> target, List<RetrievalCandidate> source) {
        if (source == null) {
            return;
        }
        for (RetrievalCandidate candidate : source) {
            target.merge(candidate.chunkId(), candidate, RetrievalCandidate::merge);
        }
    }

    private double reciprocal(Integer rank) {
        return rank == null ? 0 : 1.0 / (rankConstant + rank);
    }

    private record Scored(RetrievalCandidate candidate, double score) {
    }
}
