package com.agentto.rag.maintenance;

public record CleanupResult(
        int documents,
        int versions,
        int chunks,
        int ingestionJobs,
        int queryTraces,
        boolean elasticsearchCleared,
        boolean minioCleared) {
}
