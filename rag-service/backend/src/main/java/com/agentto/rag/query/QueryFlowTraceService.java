package com.agentto.rag.query;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentto.rag.common.api.BusinessException;
import com.agentto.rag.observability.ExecutionEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 公共查询编排 Trace 服务。
 *
 * <p>实现 {@link QueryFlowTraceRecorder} 持久化编排 Trace；
 * 同时提供管理端列表与详情查询。
 * 记录失败只记日志，不影响查询主链路。
 */
@Service
public class QueryFlowTraceService implements QueryFlowTraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(QueryFlowTraceService.class);

    private final QueryFlowTraceRepository traceRepository;
    private final ObjectMapper objectMapper;

    public QueryFlowTraceService(QueryFlowTraceRepository traceRepository, ObjectMapper objectMapper) {
        this.traceRepository = traceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 记录一次公共查询编排 Trace。
     * 序列化或持久化失败时记录警告并忽略，保证诊断能力不破坏查询主链路。
     *
     * @param trace 编排 Trace 快照
     */
    @Override
    public void record(QueryFlowTrace trace) {
        try {
            QueryFlowTraceEntity entity = QueryFlowTraceEntity.create(
                    QueryFlowTraceEntity.newTraceUid(), trace,
                    json(trace.profileShortlist()), json(trace.selectedKnowledgeBases()),
                    json(trace.traceUids()), json(trace.events()));
            traceRepository.save(entity);
        } catch (RuntimeException exception) {
            log.warn("公共查询 Trace 记录失败，已忽略：{}", exception.getMessage());
        }
    }

    /**
     * 查询最近的编排 Trace 摘要列表。
     *
     * @param limit 条数上限（1-50）
     * @return 摘要列表
     */
    @Transactional(readOnly = true)
    public List<QueryFlowTraceSummary> recent(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return traceRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .limit(safeLimit)
                .map(this::toSummary)
                .toList();
    }

    /**
     * 查询编排 Trace 详情。
     *
     * @param flowTraceUid 编排 Trace ID
     * @return 详情
     */
    @Transactional(readOnly = true)
    public QueryFlowTraceDetail detail(String flowTraceUid) {
        QueryFlowTraceEntity trace = traceRepository.findByFlowTraceUid(flowTraceUid)
                .orElseThrow(() -> new BusinessException("TRACE_NOT_FOUND", "公共查询记录不存在",
                        HttpStatus.NOT_FOUND));
        return new QueryFlowTraceDetail(trace.getFlowTraceUid(), trace.getClientAppId(),
                trace.getOriginalQuery(), trace.getEffectiveQuery(), trace.getFinalLimit(),
                trace.getDecision(), trace.getRoutingDecision(),
                list(trace.getProfileShortlistJson(), new TypeReference<List<Long>>() {
                }),
                list(trace.getSelectedKbIdsJson(),
                        new TypeReference<List<QueryFlowTrace.KnowledgeBaseSelection>>() {
                        }),
                trace.getEvidenceDecision(), trace.isRewriteAttempted(), trace.getCitationValid(),
                trace.getFailureCode(), trace.getAttemptCount(),
                list(trace.getTraceUidsJson(), new TypeReference<List<String>>() {
                }),
                list(trace.getEventsJson(), new TypeReference<List<ExecutionEvent>>() {
                }),
                trace.getAnswerLength(), trace.getTotalMs(), trace.getCreatedAt());
    }

    private QueryFlowTraceSummary toSummary(QueryFlowTraceEntity trace) {
        return new QueryFlowTraceSummary(trace.getFlowTraceUid(), trace.getClientAppId(),
                trace.getOriginalQuery(), trace.getDecision(), trace.getAttemptCount(),
                trace.getTotalMs(), trace.getCreatedAt());
    }

    /** 序列化为 JSON，失败视为不可恢复的内部错误 */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("公共查询 Trace 序列化失败", exception);
        }
    }

    /** 反序列化 JSON 列表，损坏时返回空列表（诊断数据不允许破坏查询链路） */
    private <T> List<T> list(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
