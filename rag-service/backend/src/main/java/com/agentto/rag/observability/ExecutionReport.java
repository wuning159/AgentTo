package com.agentto.rag.observability;

import java.util.List;

public record ExecutionReport(boolean historicalSnapshot, List<ExecutionEvent> events) {
    public ExecutionReport {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
