package com.agentto.rag.retrieval;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentto.rag.common.api.BusinessException;
import com.agentto.rag.observability.ExecutionReport;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class QueryTraceService implements TraceRecorder {

    private final QueryTraceRepository traceRepository;
    private final QueryCandidateRepository candidateRepository;
    private final ObjectMapper objectMapper;

    public QueryTraceService(QueryTraceRepository traceRepository, QueryCandidateRepository candidateRepository,
            ObjectMapper objectMapper) {
        this.traceRepository = traceRepository;
        this.candidateRepository = candidateRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public String record(RetrievalRequest request, List<RetrievalCandidate> candidates,
            RetrievalTimings timings, String fallbackReason) {
        return record(request, candidates, timings, fallbackReason,
                new RetrievalTraceContext(60, 0, new ExecutionReport(false, List.of())));
    }

    @Override
    @Transactional
    public String record(RetrievalRequest request, List<RetrievalCandidate> candidates,
            RetrievalTimings timings, String fallbackReason, RetrievalTraceContext context) {
        QueryTraceEntity trace = traceRepository.save(
                QueryTraceEntity.create(request, timings, fallbackReason, selectedCount(candidates),
                        context.rankConstant(), context.duplicateCount(), json(context.report())));
        candidateRepository.saveAll(candidates.stream()
                .map(candidate -> QueryCandidateEntity.create(trace.getId(), candidate))
                .toList());
        return trace.getTraceUid();
    }

    @Transactional(readOnly = true)
    public List<QueryTraceSummary> recent(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return traceRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .limit(safeLimit)
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public QueryTraceDetail detail(String traceUid) {
        QueryTraceEntity trace = traceRepository.findByTraceUid(traceUid)
                .orElseThrow(() -> new BusinessException("TRACE_NOT_FOUND", "检索记录不存在", HttpStatus.NOT_FOUND));
        List<QueryTraceCandidate> candidates = candidateRepository
                .findByTraceIdOrderByFinalRankAscRrfRankAsc(trace.getId()).stream()
                .map(this::toCandidate)
                .toList();
        return new QueryTraceDetail(trace.getTraceUid(), trace.getQueryText(), trace.getRetrievalMode(),
                trace.getFallbackReason(), trace.getResultCount(),
                new RetrievalLimits(trace.getKeywordLimit(), trace.getVectorLimit(), trace.getFusionLimit(),
                        trace.getRerankLimit(), trace.getFinalLimit()),
                trace.getRankConstant(), trace.getDeduplicatedCount(),
                new RetrievalTimings(value(trace.getEmbeddingMs()), value(trace.getKeywordMs()),
                        value(trace.getVectorMs()), value(trace.getFusionMs()), value(trace.getRerankMs()),
                        trace.getTotalMs()),
                report(trace.getExecutionReportJson()), candidates, trace.getCreatedAt());
    }

    private int selectedCount(List<RetrievalCandidate> candidates) {
        return (int) candidates.stream().filter(candidate -> candidate.finalRank() != null).count();
    }

    private QueryTraceSummary toSummary(QueryTraceEntity trace) {
        return new QueryTraceSummary(trace.getTraceUid(), trace.getQueryText(), trace.getRetrievalMode(),
                trace.getFallbackReason(), trace.getTotalMs(), trace.getResultCount(), trace.getCreatedAt());
    }

    private QueryTraceCandidate toCandidate(QueryCandidateEntity value) {
        return new QueryTraceCandidate(value.getChunkUid(), value.getContentHash(), value.getDedupeStatus(),
                value.getDuplicateOfChunkUid(), value.getTitle(), value.getContent(),
                value.getDocumentId(), value.getVersionId(), value.getMetadataJson(),
                value.getKeywordScore(), value.getKeywordRank(),
                value.getVectorScore(), value.getVectorRank(), value.getRrfScore(), value.getRrfRank(),
                value.getRerankScore(), value.getRerankRank(), value.getFinalRank(), value.isSelected());
    }

    private long value(Long nullable) {
        return nullable == null ? 0 : nullable;
    }

    private String json(ExecutionReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception exception) {
            throw new IllegalStateException("检索执行报告序列化失败", exception);
        }
    }

    private ExecutionReport report(String json) {
        if (json == null || json.isBlank()) return new ExecutionReport(false, List.of());
        try {
            return objectMapper.readValue(json, ExecutionReport.class);
        } catch (Exception ignored) {
            return new ExecutionReport(false, List.of());
        }
    }
}
