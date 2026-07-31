package com.agentto.rag.dashboard;

import java.util.List;

public record DashboardOverview(
        long totalDocuments,
        long readyDocuments,
        long processingDocuments,
        long failedDocuments,
        long totalChunks,
        long totalTraces,
        long runningJobs,
        long failedJobs,
        List<DependencyState> dependencies) {
}
