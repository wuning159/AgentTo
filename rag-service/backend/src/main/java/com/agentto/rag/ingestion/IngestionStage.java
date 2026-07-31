package com.agentto.rag.ingestion;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "rag_ingestion_stage")
public class IngestionStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "job_id", nullable = false)
    private Long jobId;
    @Column(name = "stage_code", nullable = false)
    private String stageCode;
    @Column(nullable = false)
    private String status;
    @Column(name = "detail_message", length = 1000)
    private String detailMessage;
    @Lob
    @Column(name = "technical_detail_json", columnDefinition = "longtext")
    private String technicalDetailJson;
    @Column(name = "item_count")
    private Integer itemCount;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    protected IngestionStage() {
    }

    private IngestionStage(Long jobId, String stageCode, String status, String detailMessage, Integer itemCount,
            String technicalDetailJson, Instant startedAt, Instant finishedAt) {
        this.jobId = jobId;
        this.stageCode = stageCode;
        this.status = status;
        this.detailMessage = abbreviate(detailMessage);
        this.technicalDetailJson = technicalDetailJson;
        this.itemCount = itemCount;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.elapsedMs = finishedAt == null ? null : Duration.between(startedAt, finishedAt).toMillis();
    }

    public static IngestionStage success(Long jobId, String stageCode, String detail, Integer count,
            Instant startedAt) {
        return success(jobId, stageCode, detail, count, null, startedAt);
    }

    public static IngestionStage success(Long jobId, String stageCode, String detail, Integer count,
            String technicalDetailJson, Instant startedAt) {
        return new IngestionStage(jobId, stageCode, "SUCCEEDED", detail, count, technicalDetailJson,
                startedAt, Instant.now());
    }

    public static IngestionStage failed(Long jobId, String stageCode, String detail, Instant startedAt) {
        return failed(jobId, stageCode, detail, null, startedAt);
    }

    public static IngestionStage failed(Long jobId, String stageCode, String detail, String technicalDetailJson,
            Instant startedAt) {
        return new IngestionStage(jobId, stageCode, "FAILED", detail, null, technicalDetailJson,
                startedAt, Instant.now());
    }

    private String abbreviate(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public Long getId() { return id; }
    public String getStageCode() { return stageCode; }
    public String getStatus() { return status; }
    public String getDetailMessage() { return detailMessage; }
    public String getTechnicalDetailJson() { return technicalDetailJson; }
    public Integer getItemCount() { return itemCount; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Long getElapsedMs() { return elapsedMs; }
}
