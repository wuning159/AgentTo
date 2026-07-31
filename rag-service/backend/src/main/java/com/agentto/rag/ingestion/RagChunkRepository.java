package com.agentto.rag.ingestion;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RagChunkRepository extends JpaRepository<RagChunkEntity, Long> {
    List<RagChunkEntity> findByVersionIdOrderByOrdinalNo(Long versionId);
    Page<RagChunkEntity> findByVersionIdOrderByOrdinalNo(Long versionId, Pageable pageable);
    void deleteByVersionId(Long versionId);
    long countByDocumentId(Long documentId);
}
