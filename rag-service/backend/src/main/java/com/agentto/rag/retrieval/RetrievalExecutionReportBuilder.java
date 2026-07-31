package com.agentto.rag.retrieval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.agentto.rag.observability.ExecutionEvent;
import com.agentto.rag.observability.ExecutionReport;
import com.agentto.rag.observability.TechnicalStageDetail;

final class RetrievalExecutionReportBuilder {

    private final List<ExecutionEvent> events = new ArrayList<>();

    void completed(RetrievalStage stage, Instant startedAt, long elapsedMs,
            TechnicalStageDetail detail) {
        add(stage, "COMPLETED", startedAt, elapsedMs, detail);
    }

    void degraded(RetrievalStage stage, Instant startedAt, long elapsedMs,
            TechnicalStageDetail detail) {
        add(stage, "DEGRADED", startedAt, elapsedMs, detail);
    }

    void failed(RetrievalStage stage, Instant startedAt, long elapsedMs,
            TechnicalStageDetail detail) {
        add(stage, "FAILED", startedAt, elapsedMs, detail);
    }

    void skipped(RetrievalStage stage, String summary) {
        Instant now = Instant.now();
        add(stage, "SKIPPED", now, 0,
                new TechnicalStageDetail(summary, null, null, null, null, null, null));
    }

    ExecutionReport report() {
        return new ExecutionReport(true, List.copyOf(events));
    }

    private void add(RetrievalStage stage, String status, Instant startedAt, long elapsedMs,
            TechnicalStageDetail detail) {
        Instant finishedAt = Instant.now();
        events.add(new ExecutionEvent(stage.name(), status, startedAt.toString(),
                finishedAt.toString(), elapsedMs, detail));
    }
}
