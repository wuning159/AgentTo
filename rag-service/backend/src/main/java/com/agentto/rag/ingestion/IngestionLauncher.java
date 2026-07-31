package com.agentto.rag.ingestion;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class IngestionLauncher {

    private final IngestionOrchestrator orchestrator;

    public IngestionLauncher(IngestionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Async("ragTaskExecutor")
    public void launch(Long jobId) {
        orchestrator.process(jobId);
    }
}
