package com.agentto.rag.query;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * 公共查询编排 Trace 持久化实体。
 *
 * <p>对应 rag_query_flow_trace 表，JSON 列由服务层负责序列化与反序列化。
 */
@Entity
@Table(name = "rag_query_flow_trace")
public class QueryFlowTraceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "flow_trace_uid", nullable = false, unique = true, length = 64)
    private String flowTraceUid;
    @Column(name = "client_app_id", nullable = false)
    private Long clientAppId;
    @Column(name = "original_query", nullable = false, length = 2000)
    private String originalQuery;
    @Column(name = "effective_query", nullable = false, length = 2000)
    private String effectiveQuery;
    @Column(name = "final_limit", nullable = false)
    private int finalLimit;
    @Column(name = "decision", nullable = false, length = 32)
    private String decision;
    @Column(name = "routing_decision", nullable = false, length = 32)
    private String routingDecision;
    @Lob
    @Column(name = "profile_shortlist_json", columnDefinition = "text")
    private String profileShortlistJson;
    @Lob
    @Column(name = "selected_kb_ids_json", columnDefinition = "text")
    private String selectedKbIdsJson;
    @Column(name = "evidence_decision", length = 32)
    private String evidenceDecision;
    @Column(name = "rewrite_attempted", nullable = false)
    private boolean rewriteAttempted;
    @Column(name = "citation_valid")
    private Boolean citationValid;
    @Column(name = "failure_code", length = 64)
    private String failureCode;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Lob
    @Column(name = "trace_uids_json", columnDefinition = "text")
    private String traceUidsJson;
    @Lob
    @Column(name = "events_json", columnDefinition = "longtext")
    private String eventsJson;
    @Column(name = "answer_length")
    private Integer answerLength;
    @Column(name = "total_ms", nullable = false)
    private long totalMs;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QueryFlowTraceEntity() {
    }

    /**
     * 从编排 Trace 快照创建实体。
     * 所有 JSON 列由调用方传入已序列化文本，保证服务层持有序列化职责。
     *
     * @param flowTraceUid   编排 Trace ID
     * @param trace          编排 Trace 快照
     * @param profileShortlistJson  画像召回列表 JSON
     * @param selectedKbIdsJson     选中知识库列表 JSON
     * @param traceUidsJson         关联检索 Trace ID 列表 JSON
     * @param eventsJson            阶段事件列表 JSON
     * @return 实体
     */
    public static QueryFlowTraceEntity create(String flowTraceUid, QueryFlowTrace trace,
            String profileShortlistJson, String selectedKbIdsJson, String traceUidsJson,
            String eventsJson) {
        QueryFlowTraceEntity entity = new QueryFlowTraceEntity();
        entity.flowTraceUid = flowTraceUid;
        entity.clientAppId = trace.clientAppId();
        entity.originalQuery = trace.originalQuery();
        entity.effectiveQuery = trace.effectiveQuery();
        entity.finalLimit = trace.finalLimit();
        entity.decision = trace.decision().name();
        entity.routingDecision = trace.routingDecision().name();
        entity.profileShortlistJson = profileShortlistJson;
        entity.selectedKbIdsJson = selectedKbIdsJson;
        entity.evidenceDecision = trace.evidenceDecision() == null ? null
                : trace.evidenceDecision().name();
        entity.rewriteAttempted = trace.rewriteAttempted();
        entity.citationValid = trace.citationValid();
        entity.failureCode = trace.failureCode() == null ? null : trace.failureCode().name();
        entity.attemptCount = trace.attemptCount();
        entity.traceUidsJson = traceUidsJson;
        entity.eventsJson = eventsJson;
        entity.answerLength = trace.answerLength();
        entity.totalMs = trace.totalMs();
        entity.createdAt = Instant.now();
        return entity;
    }

    /** 生成新的编排 Trace ID */
    public static String newTraceUid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public Long getId() { return id; }
    public String getFlowTraceUid() { return flowTraceUid; }
    public Long getClientAppId() { return clientAppId; }
    public String getOriginalQuery() { return originalQuery; }
    public String getEffectiveQuery() { return effectiveQuery; }
    public int getFinalLimit() { return finalLimit; }
    public String getDecision() { return decision; }
    public String getRoutingDecision() { return routingDecision; }
    public String getProfileShortlistJson() { return profileShortlistJson; }
    public String getSelectedKbIdsJson() { return selectedKbIdsJson; }
    public String getEvidenceDecision() { return evidenceDecision; }
    public boolean isRewriteAttempted() { return rewriteAttempted; }
    public Boolean getCitationValid() { return citationValid; }
    public String getFailureCode() { return failureCode; }
    public int getAttemptCount() { return attemptCount; }
    public String getTraceUidsJson() { return traceUidsJson; }
    public String getEventsJson() { return eventsJson; }
    public Integer getAnswerLength() { return answerLength; }
    public long getTotalMs() { return totalMs; }
    public Instant getCreatedAt() { return createdAt; }
}
