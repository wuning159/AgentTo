package com.agentto.rag.ingestion;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {
    List<IngestionJob> findTop10ByOrderByCreatedAtDesc();
    Optional<IngestionJob> findFirstByVersionIdOrderByCreatedAtDesc(Long versionId);
    long countByStatus(String status);
}
