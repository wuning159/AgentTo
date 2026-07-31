package com.agentto.rag.ingestion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionStageRepository extends JpaRepository<IngestionStage, Long> {
    List<IngestionStage> findByJobIdOrderById(Long jobId);
}
