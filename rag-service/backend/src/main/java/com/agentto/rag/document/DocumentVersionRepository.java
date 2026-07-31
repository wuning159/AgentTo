package com.agentto.rag.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<RagDocumentVersion, Long> {
    List<RagDocumentVersion> findByDocumentIdOrderByVersionNoDesc(Long documentId);
    Optional<RagDocumentVersion> findFirstBySha256OrderByCreatedAtDesc(String sha256);
}
