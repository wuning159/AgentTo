package com.agentto.rag.ingestion;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rag_ingestion_job")
public class IngestionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "document_id", nullable = false)
    private Long documentId;
    @Column(name = "version_id", nullable = false)
    private Long versionId;
    @Column(nullable = false)
    private String status;
    @Column(name = "current_stage")
    private String currentStage;
    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;
    @Column(name = "error_code")
    private String errorCode;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IngestionJob() {
    }

    private IngestionJob(Long documentId, Long versionId) {
        this.documentId = documentId;
        this.versionId = versionId;
        this.status = "QUEUED";
        this.attemptNo = 1;
        this.createdAt = Instant.now();
    }

    public static IngestionJob queued(Long documentId, Long versionId) { return new IngestionJob(documentId, versionId); }
    public void start(String stage) { status = "RUNNING"; currentStage = stage; startedAt = Instant.now(); }
    public void stage(String stage) { currentStage = stage; }
    public void succeed() { status = "SUCCEEDED"; currentStage = "COMPLETE"; finishedAt = Instant.now(); }
    public void fail(String stage, String message) { status = "FAILED"; currentStage = stage; errorCode = "INGESTION_FAILED"; errorMessage = abbreviate(message); finishedAt = Instant.now(); }

    private String abbreviate(String value) { if (value == null) return "未知错误"; return value.length() <= 1000 ? value : value.substring(0, 1000); }
    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public Long getVersionId() { return versionId; }
    public String getStatus() { return status; }
    public String getCurrentStage() { return currentStage; }
    public int getAttemptNo() { return attemptNo; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
