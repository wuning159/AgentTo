package com.agentto.rag.retrieval;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "rag_query_trace")
public class QueryTraceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "trace_uid", nullable = false, unique = true, length = 64)
    private String traceUid;
    @Column(name = "query_text", nullable = false, length = 2000)
    private String queryText;
    @Column(name = "retrieval_mode", nullable = false, length = 32)
    private String retrievalMode;
    @Column(name = "filter_json")
    private String filterJson;
    @Column(name = "keyword_limit", nullable = false)
    private int keywordLimit;
    @Column(name = "vector_limit", nullable = false)
    private int vectorLimit;
    @Column(name = "fusion_limit", nullable = false)
    private int fusionLimit;
    @Column(name = "rerank_limit", nullable = false)
    private int rerankLimit;
    @Column(name = "final_limit", nullable = false)
    private int finalLimit;
    @Column(name = "rank_constant", nullable = false)
    private int rankConstant;
    @Column(name = "fallback_reason", length = 512)
    private String fallbackReason;
    @Column(name = "embedding_ms")
    private Long embeddingMs;
    @Column(name = "keyword_ms")
    private Long keywordMs;
    @Column(name = "vector_ms")
    private Long vectorMs;
    @Column(name = "fusion_ms")
    private Long fusionMs;
    @Column(name = "rerank_ms")
    private Long rerankMs;
    @Column(name = "total_ms", nullable = false)
    private long totalMs;
    @Column(name = "result_count", nullable = false)
    private int resultCount;
    @Column(name = "deduplicated_count", nullable = false)
    private int deduplicatedCount;
    @Lob
    @Column(name = "execution_report_json", columnDefinition = "longtext")
    private String executionReportJson;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QueryTraceEntity() {
    }

    public static QueryTraceEntity create(RetrievalRequest request, RetrievalTimings timings,
            String fallbackReason, int resultCount) {
        return create(request, timings, fallbackReason, resultCount, 60, 0, null);
    }

    public static QueryTraceEntity create(RetrievalRequest request, RetrievalTimings timings,
            String fallbackReason, int resultCount, int rankConstant, int deduplicatedCount,
            String executionReportJson) {
        if (request.requesterId() == null) {
            throw new IllegalArgumentException("记录检索 Trace 时必须提供操作人");
        }
        QueryTraceEntity entity = new QueryTraceEntity();
        entity.traceUid = UUID.randomUUID().toString().replace("-", "");
        entity.queryText = request.query();
        entity.retrievalMode = "HYBRID";
        entity.keywordLimit = request.keywordLimit();
        entity.vectorLimit = request.vectorLimit();
        entity.fusionLimit = request.fusionLimit();
        entity.rerankLimit = request.rerankLimit();
        entity.finalLimit = request.finalLimit();
        entity.rankConstant = rankConstant;
        entity.fallbackReason = abbreviate(fallbackReason, 512);
        entity.embeddingMs = timings.embeddingMs();
        entity.keywordMs = timings.keywordMs();
        entity.vectorMs = timings.vectorMs();
        entity.fusionMs = timings.fusionMs();
        entity.rerankMs = timings.rerankMs();
        entity.totalMs = timings.totalMs();
        entity.resultCount = resultCount;
        entity.deduplicatedCount = deduplicatedCount;
        entity.executionReportJson = executionReportJson;
        entity.createdBy = request.requesterId();
        entity.createdAt = Instant.now();
        return entity;
    }

    private static String abbreviate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() { return id; }
    public String getTraceUid() { return traceUid; }
    public String getQueryText() { return queryText; }
    public String getRetrievalMode() { return retrievalMode; }
    public int getKeywordLimit() { return keywordLimit; }
    public int getVectorLimit() { return vectorLimit; }
    public int getFusionLimit() { return fusionLimit; }
    public int getRerankLimit() { return rerankLimit; }
    public int getFinalLimit() { return finalLimit; }
    public int getRankConstant() { return rankConstant; }
    public String getFallbackReason() { return fallbackReason; }
    public Long getEmbeddingMs() { return embeddingMs; }
    public Long getKeywordMs() { return keywordMs; }
    public Long getVectorMs() { return vectorMs; }
    public Long getFusionMs() { return fusionMs; }
    public Long getRerankMs() { return rerankMs; }
    public long getTotalMs() { return totalMs; }
    public int getResultCount() { return resultCount; }
    public int getDeduplicatedCount() { return deduplicatedCount; }
    public String getExecutionReportJson() { return executionReportJson; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
