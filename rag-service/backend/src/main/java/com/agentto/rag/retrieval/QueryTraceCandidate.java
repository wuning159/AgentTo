package com.agentto.rag.retrieval;

public record QueryTraceCandidate(
        String chunkId,
        String contentHash,
        String dedupeStatus,
        String duplicateOfChunkId,
        String title,
        String content,
        Long documentId,
        Long versionId,
        String metadataJson,
        Double keywordScore,
        Integer keywordRank,
        Double vectorScore,
        Integer vectorRank,
        Double rrfScore,
        Integer rrfRank,
        Double rerankScore,
        Integer rerankRank,
        Integer finalRank,
        boolean selected) {
}
