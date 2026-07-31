package com.agentto.rag.retrieval;

public record RerankScore(int originalIndex, double score, int rank) {
}
