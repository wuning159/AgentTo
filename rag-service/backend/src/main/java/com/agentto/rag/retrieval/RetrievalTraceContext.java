package com.agentto.rag.retrieval;

import com.agentto.rag.observability.ExecutionReport;

public record RetrievalTraceContext(
        int rankConstant,
        int duplicateCount,
        ExecutionReport report) {
}
