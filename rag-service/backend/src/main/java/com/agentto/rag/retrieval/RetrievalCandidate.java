package com.agentto.rag.retrieval;

import java.util.Map;

import com.agentto.rag.index.IndexSearchHit;

public record RetrievalCandidate(
        String chunkId,
        String content,
        String title,
        Long documentId,
        Long versionId,
        Integer ordinal,
        Map<String, String> metadata,
        Double keywordScore,
        Integer keywordRank,
        Double vectorScore,
        Integer vectorRank,
        Double rrfScore,
        Integer rrfRank,
        Double rerankScore,
        Integer rerankRank,
        Integer finalRank,
        String contentHash,
        DedupeStatus dedupeStatus,
        String duplicateOfChunkId) {

    public static RetrievalCandidate keyword(String chunkId, String content, double score, int rank) {
        return new RetrievalCandidate(chunkId, content, null, null, null, null, Map.of(),
                score, rank, null, null, null, null, null, null, null,
                null, DedupeStatus.PENDING, null);
    }

    public static RetrievalCandidate vector(String chunkId, String content, double score, int rank) {
        return new RetrievalCandidate(chunkId, content, null, null, null, null, Map.of(),
                null, null, score, rank, null, null, null, null, null,
                null, DedupeStatus.PENDING, null);
    }

    public static RetrievalCandidate keyword(IndexSearchHit hit, int rank) {
        return fromHit(hit, hit.score(), rank, null, null);
    }

    public static RetrievalCandidate vector(IndexSearchHit hit, int rank) {
        return fromHit(hit, null, null, hit.score(), rank);
    }

    private static RetrievalCandidate fromHit(IndexSearchHit hit, Double keywordScore, Integer keywordRank,
            Double vectorScore, Integer vectorRank) {
        return new RetrievalCandidate(hit.chunkId(), hit.content(), hit.title(), hit.documentId(), hit.versionId(),
                hit.ordinal(), hit.metadata() == null ? Map.of() : Map.copyOf(hit.metadata()),
                keywordScore, keywordRank, vectorScore, vectorRank, null, null, null, null, null,
                null, DedupeStatus.PENDING, null);
    }

    RetrievalCandidate merge(RetrievalCandidate other) {
        String mergedContent = content == null || content.isBlank() ? other.content : content;
        return new RetrievalCandidate(chunkId, mergedContent,
                first(title, other.title), first(documentId, other.documentId), first(versionId, other.versionId),
                first(ordinal, other.ordinal), metadata == null || metadata.isEmpty() ? other.metadata : metadata,
                keywordScore != null ? keywordScore : other.keywordScore,
                keywordRank != null ? keywordRank : other.keywordRank,
                vectorScore != null ? vectorScore : other.vectorScore,
                vectorRank != null ? vectorRank : other.vectorRank,
                rrfScore, rrfRank, rerankScore, rerankRank, finalRank,
                contentHash, dedupeStatus, duplicateOfChunkId);
    }

    RetrievalCandidate withRrf(double score, int rank) {
        return new RetrievalCandidate(chunkId, content, title, documentId, versionId, ordinal, metadata,
                keywordScore, keywordRank, vectorScore, vectorRank,
                score, rank, rerankScore, rerankRank, finalRank,
                contentHash, dedupeStatus, duplicateOfChunkId);
    }

    public RetrievalCandidate withRerank(double score, int rank) {
        return new RetrievalCandidate(chunkId, content, title, documentId, versionId, ordinal, metadata,
                keywordScore, keywordRank, vectorScore, vectorRank,
                rrfScore, rrfRank, score, rank, rank,
                contentHash, dedupeStatus, duplicateOfChunkId);
    }

    public RetrievalCandidate withFinalRank(int rank) {
        return new RetrievalCandidate(chunkId, content, title, documentId, versionId, ordinal, metadata,
                keywordScore, keywordRank, vectorScore, vectorRank,
                rrfScore, rrfRank, rerankScore, rerankRank, rank,
                contentHash, dedupeStatus, duplicateOfChunkId);
    }

    RetrievalCandidate withDedupe(String hash, DedupeStatus status, String duplicateOf) {
        return new RetrievalCandidate(chunkId, content, title, documentId, versionId, ordinal, metadata,
                keywordScore, keywordRank, vectorScore, vectorRank,
                rrfScore, rrfRank, rerankScore, rerankRank, finalRank,
                hash, status, duplicateOf);
    }

    private static <T> T first(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
