package com.agentto.rag.retrieval;

public interface RetrievalProgressReporter {

    void running(RetrievalStage stage);

    void completed(RetrievalStage stage, long elapsedMs, Integer itemCount);

    void degraded(RetrievalStage stage, long elapsedMs, String message);

    void skipped(RetrievalStage stage, String message);

    void failed(RetrievalStage stage, String message);

    static RetrievalProgressReporter noop() {
        return NoopHolder.INSTANCE;
    }

    final class NoopHolder {
        private static final RetrievalProgressReporter INSTANCE = new RetrievalProgressReporter() {
            @Override public void running(RetrievalStage stage) { }
            @Override public void completed(RetrievalStage stage, long elapsedMs, Integer itemCount) { }
            @Override public void degraded(RetrievalStage stage, long elapsedMs, String message) { }
            @Override public void skipped(RetrievalStage stage, String message) { }
            @Override public void failed(RetrievalStage stage, String message) { }
        };

        private NoopHolder() { }
    }
}
