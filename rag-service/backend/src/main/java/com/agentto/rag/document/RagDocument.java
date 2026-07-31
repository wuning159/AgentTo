package com.agentto.rag.document;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rag_document")
public class RagDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(nullable = false)
    private String status;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RagDocument() {
    }

    private RagDocument(String name, String category, Long knowledgeBaseId, Long createdBy) {
        this.name = name;
        this.category = category;
        this.knowledgeBaseId = knowledgeBaseId;
        this.sourceType = "MANUAL";
        this.status = "PROCESSING";
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static RagDocument manual(String name, String category, Long knowledgeBaseId, Long createdBy) {
        return new RagDocument(name, category, knowledgeBaseId, createdBy);
    }

    public void setCurrentVersion(Long versionId) {
        currentVersionId = versionId;
        updatedAt = Instant.now();
    }

    public void markReady() {
        status = "READY";
        updatedAt = Instant.now();
    }

    public void markFailed() {
        status = "FAILED";
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getSourceType() { return sourceType; }
    public String getStatus() { return status; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public Long getCurrentVersionId() { return currentVersionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
