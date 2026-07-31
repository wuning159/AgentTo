package com.agentto.rag.retrieval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.agentto.rag.embedding.EmbeddingService;
import com.agentto.rag.index.ChunkIndex;
import com.agentto.rag.index.IndexSearchHit;
import com.agentto.rag.index.SearchScope;
import com.agentto.rag.observability.TechnicalStageDetail;

@Service
public class HybridRetrievalService {

    private final EmbeddingService embeddingService;
    private final ChunkIndex chunkIndex;
    private final RerankService rerankService;
    private final TraceRecorder traceRecorder;
    private final RrfFusion fusion;
    private final ContentDeduplicator contentDeduplicator;

    @Autowired
    public HybridRetrievalService(EmbeddingService embeddingService, ChunkIndex chunkIndex,
            RerankService rerankService, TraceRecorder traceRecorder) {
        this(embeddingService, chunkIndex, rerankService, traceRecorder, new RrfFusion(60),
                new ContentDeduplicator());
    }

    HybridRetrievalService(EmbeddingService embeddingService, ChunkIndex chunkIndex,
            RerankService rerankService, TraceRecorder traceRecorder, RrfFusion fusion) {
        this(embeddingService, chunkIndex, rerankService, traceRecorder, fusion, new ContentDeduplicator());
    }

    HybridRetrievalService(EmbeddingService embeddingService, ChunkIndex chunkIndex,
            RerankService rerankService, TraceRecorder traceRecorder, RrfFusion fusion,
            ContentDeduplicator contentDeduplicator) {
        this.embeddingService = embeddingService;
        this.chunkIndex = chunkIndex;
        this.rerankService = rerankService;
        this.traceRecorder = traceRecorder;
        this.fusion = fusion;
        this.contentDeduplicator = contentDeduplicator;
    }

    public RetrievalResponse search(RetrievalRequest request) {
        return search(request, RetrievalProgressReporter.noop());
    }

    public RetrievalResponse search(RetrievalRequest request, RetrievalProgressReporter reporter) {
        long totalStarted = System.nanoTime();
        RetrievalExecutionReportBuilder report = new RetrievalExecutionReportBuilder();

        reporter.running(RetrievalStage.PREPROCESS);
        Instant preprocessAt = Instant.now();
        long preprocessStarted = System.nanoTime();
        String query = request.query().strip().replaceAll("\\s+", " ");
        long preprocessMs = elapsedMs(preprocessStarted);
        reporter.completed(RetrievalStage.PREPROCESS, preprocessMs, 1);
        report.completed(RetrievalStage.PREPROCESS, preprocessAt, preprocessMs,
                detail("去除查询首尾空白并合并连续空白字符。", 1, 1,
                        Map.of("normalization", "trim + collapse whitespace"),
                        Map.of("originalLength", request.query().length(), "normalizedLength", query.length()),
                        List.of(Map.of("normalizedQuery", query))));

        reporter.running(RetrievalStage.KEYWORD);
        Instant keywordAt = Instant.now();
        long keywordStarted = System.nanoTime();
        List<RetrievalCandidate> keyword;
        try {
            keyword = toCandidates(keywordSearch(query, request.scope(), request.keywordLimit()), true);
        } catch (RuntimeException exception) {
            long elapsed = elapsedMs(keywordStarted);
            reporter.failed(RetrievalStage.KEYWORD, failureMessage(exception));
            report.failed(RetrievalStage.KEYWORD, keywordAt, elapsed,
                    detail("关键词召回执行失败。", 1, 0,
                            Map.of("limit", request.keywordLimit()),
                            Map.of("error", failureMessage(exception)), List.of()));
            throw exception;
        }
        long keywordMs = elapsedMs(keywordStarted);
        reporter.completed(RetrievalStage.KEYWORD, keywordMs, keyword.size());
        report.completed(RetrievalStage.KEYWORD, keywordAt, keywordMs,
                detail("通过 Elasticsearch BM25 关键词检索得到候选分块。", 1, keyword.size(),
                        Map.of("limit", request.keywordLimit(), "indexVersion", chunkIndex.indexVersion()),
                        Map.of("elapsedMs", keywordMs), candidateSamples(keyword)));

        String fallbackReason = null;
        long embeddingMs = 0;
        long vectorMs = 0;
        List<RetrievalCandidate> vector = List.of();
        reporter.running(RetrievalStage.EMBEDDING);
        Instant embeddingAt = Instant.now();
        float[] queryVector = null;
        long embeddingStarted = System.nanoTime();
        try {
            queryVector = embeddingService.embed(List.of(query)).get(0);
            embeddingMs = elapsedMs(embeddingStarted);
            reporter.completed(RetrievalStage.EMBEDDING, embeddingMs, null);
            report.completed(RetrievalStage.EMBEDDING, embeddingAt, embeddingMs,
                    detail("把查询文本转换为向量，供语义召回使用。", 1, 1,
                            Map.of("provider", embeddingService.getClass().getSimpleName()),
                            Map.of("dimensions", queryVector.length, "elapsedMs", embeddingMs), List.of()));
        } catch (RuntimeException exception) {
            embeddingMs = elapsedMs(embeddingStarted);
            reporter.degraded(RetrievalStage.EMBEDDING, embeddingMs, failureMessage(exception));
            reporter.skipped(RetrievalStage.VECTOR, "查询向量生成失败，已跳过向量召回");
            report.degraded(RetrievalStage.EMBEDDING, embeddingAt, embeddingMs,
                    detail("查询向量生成失败，检索已降级为关键词召回。", 1, 0,
                            Map.of("provider", embeddingService.getClass().getSimpleName()),
                            Map.of("error", failureMessage(exception)), List.of()));
            report.skipped(RetrievalStage.VECTOR, "查询向量生成失败，因此没有执行向量召回。");
            fallbackReason = fallback("EMBEDDING_UNAVAILABLE", exception);
        }

        if (queryVector != null) {
            reporter.running(RetrievalStage.VECTOR);
            Instant vectorAt = Instant.now();
            long vectorStarted = System.nanoTime();
            try {
                vector = toCandidates(vectorSearch(queryVector, request.scope(), request.vectorLimit()), false);
            } catch (RuntimeException exception) {
                long elapsed = elapsedMs(vectorStarted);
                reporter.failed(RetrievalStage.VECTOR, failureMessage(exception));
                report.failed(RetrievalStage.VECTOR, vectorAt, elapsed,
                        detail("向量召回执行失败。", 1, 0,
                                Map.of("limit", request.vectorLimit()),
                                Map.of("error", failureMessage(exception)), List.of()));
                throw exception;
            }
            vectorMs = elapsedMs(vectorStarted);
            reporter.completed(RetrievalStage.VECTOR, vectorMs, vector.size());
            report.completed(RetrievalStage.VECTOR, vectorAt, vectorMs,
                    detail("在 Elasticsearch 向量索引中查找语义相近的分块。", 1, vector.size(),
                            Map.of("limit", request.vectorLimit(), "indexVersion", chunkIndex.indexVersion()),
                            Map.of("elapsedMs", vectorMs), candidateSamples(vector)));
        }

        reporter.running(RetrievalStage.FUSION);
        Instant fusionAt = Instant.now();
        long fusionStarted = System.nanoTime();
        List<RetrievalCandidate> fusedAll;
        try {
            fusedAll = fusion.fuse(keyword, vector, keyword.size() + vector.size());
        } catch (RuntimeException exception) {
            reporter.failed(RetrievalStage.FUSION, failureMessage(exception));
            throw exception;
        }
        long fusionMs = elapsedMs(fusionStarted);
        reporter.completed(RetrievalStage.FUSION, fusionMs, fusedAll.size());
        report.completed(RetrievalStage.FUSION, fusionAt, fusionMs,
                detail("使用 RRF 合并关键词和向量两路排名；每一路贡献为 1 / (k + rank)。",
                        keyword.size() + vector.size(), fusedAll.size(),
                        Map.of("method", "RRF", "rankConstant", fusion.rankConstant(),
                                "formula", "1 / (k + rank)"),
                        Map.of("keywordCandidates", keyword.size(), "vectorCandidates", vector.size(),
                                "elapsedMs", fusionMs), candidateSamples(fusedAll)));

        reporter.running(RetrievalStage.DEDUPE);
        Instant dedupeAt = Instant.now();
        long dedupeStarted = System.nanoTime();
        DedupeResult dedupe = contentDeduplicator.dedupe(fusedAll, request.fusionLimit());
        List<RetrievalCandidate> fused = dedupe.selected();
        long dedupeMs = elapsedMs(dedupeStarted);
        reporter.completed(RetrievalStage.DEDUPE, dedupeMs, fused.size());
        report.completed(RetrievalStage.DEDUPE, dedupeAt, dedupeMs,
                detail("对规范化后的分块正文计算 SHA-256，内容相同的候选只保留排名最高的一条。",
                        fusedAll.size(), fused.size(),
                        Map.of("normalization", "Unicode NFKC + collapse whitespace",
                                "fingerprint", "SHA-256", "limitAfterDedupe", request.fusionLimit()),
                        Map.of("duplicateCount", dedupe.duplicateCount(), "elapsedMs", dedupeMs),
                        candidateSamples(dedupe.traceCandidates())));

        long rerankMs = 0;
        List<RetrievalCandidate> finalCandidates;
        List<RetrievalCandidate> traceCandidates;
        reporter.running(RetrievalStage.RERANK);
        Instant rerankAt = Instant.now();
        long rerankStarted = System.nanoTime();
        List<RetrievalCandidate> rerankInput = fused.stream().limit(request.rerankLimit()).toList();
        try {
            List<RerankScore> scores = rerankService.rerank(query,
                    rerankInput.stream().map(RetrievalCandidate::content).toList());
            rerankMs = elapsedMs(rerankStarted);
            RerankOutcome outcome = applyRerank(fused, rerankInput, scores, request.finalLimit());
            finalCandidates = outcome.finalCandidates();
            traceCandidates = mergeDedupeTrace(dedupe.traceCandidates(), outcome.traceCandidates());
            reporter.completed(RetrievalStage.RERANK, rerankMs, rerankInput.size());
            report.completed(RetrievalStage.RERANK, rerankAt, rerankMs,
                    detail("使用精排模型重新判断查询与候选分块的相关性，并产生最终顺序。",
                            rerankInput.size(), finalCandidates.size(),
                            Map.of("provider", rerankService.getClass().getSimpleName(),
                                    "rerankLimit", request.rerankLimit(), "finalLimit", request.finalLimit()),
                            Map.of("elapsedMs", rerankMs), candidateSamples(finalCandidates)));
        } catch (RuntimeException exception) {
            rerankMs = elapsedMs(rerankStarted);
            reporter.degraded(RetrievalStage.RERANK, rerankMs, failureMessage(exception));
            fallbackReason = appendFallback(fallbackReason, fallback("RERANK_UNAVAILABLE", exception));
            finalCandidates = withFinalRanks(fused, request.finalLimit());
            traceCandidates = mergeDedupeTrace(dedupe.traceCandidates(),
                    mergeFinalRanks(fused, finalCandidates));
            report.degraded(RetrievalStage.RERANK, rerankAt, rerankMs,
                    detail("精排不可用，最终结果按 RRF 顺序返回。", rerankInput.size(),
                            finalCandidates.size(),
                            Map.of("provider", rerankService.getClass().getSimpleName(),
                                    "fallback", "RRF_ORDER"),
                            Map.of("error", failureMessage(exception), "elapsedMs", rerankMs),
                            candidateSamples(finalCandidates)));
        }

        RetrievalTimings timings = new RetrievalTimings(embeddingMs, keywordMs, vectorMs, fusionMs,
                rerankMs, elapsedMs(totalStarted));
        reporter.running(RetrievalStage.COMPLETE);
        String traceUid;
        try {
            traceUid = traceRecorder.record(request, traceCandidates, timings, fallbackReason,
                    new RetrievalTraceContext(fusion.rankConstant(), dedupe.duplicateCount(), report.report()));
        } catch (RuntimeException exception) {
            reporter.failed(RetrievalStage.COMPLETE, failureMessage(exception));
            throw exception;
        }
        reporter.completed(RetrievalStage.COMPLETE, elapsedMs(totalStarted), finalCandidates.size());
        return new RetrievalResponse(traceUid, finalCandidates, fallbackReason, timings);
    }

    private TechnicalStageDetail detail(String summary, Integer inputCount, Integer outputCount,
            Map<String, Object> parameters, Map<String, Object> metrics,
            List<Map<String, Object>> samples) {
        return new TechnicalStageDetail(summary, inputCount, outputCount, parameters, metrics,
                samples, Map.of());
    }

    private List<Map<String, Object>> candidateSamples(List<RetrievalCandidate> candidates) {
        List<Map<String, Object>> samples = new ArrayList<>();
        for (RetrievalCandidate candidate : candidates.stream().limit(3).toList()) {
            Map<String, Object> sample = new HashMap<>();
            sample.put("chunkId", candidate.chunkId());
            sample.put("content", abbreviate(candidate.content(), 180));
            putIfPresent(sample, "keywordRank", candidate.keywordRank());
            putIfPresent(sample, "vectorRank", candidate.vectorRank());
            putIfPresent(sample, "rrfScore", candidate.rrfScore());
            putIfPresent(sample, "rerankScore", candidate.rerankScore());
            putIfPresent(sample, "dedupeStatus", candidate.dedupeStatus());
            samples.add(Map.copyOf(sample));
        }
        return List.copyOf(samples);
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String abbreviate(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit) + "…";
    }

    private String failureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    /**
     * 关键词检索：带知识库范围时使用限定检索，否则使用全量检索（兼容旧调用）。
     *
     * @param query 查询文本
     * @param scope 知识库范围，可为 null
     * @param limit 最大返回数量
     * @return 命中结果
     */
    private List<IndexSearchHit> keywordSearch(String query, SearchScope scope, int limit) {
        return scope == null ? chunkIndex.keywordSearch(query, limit)
                : chunkIndex.keywordSearch(query, scope, limit);
    }

    /**
     * 向量检索：带知识库范围时使用限定检索，否则使用全量检索（兼容旧调用）。
     *
     * @param queryVector 查询向量
     * @param scope       知识库范围，可为 null
     * @param limit       最大返回数量
     * @return 命中结果
     */
    private List<IndexSearchHit> vectorSearch(float[] queryVector, SearchScope scope, int limit) {
        return scope == null ? chunkIndex.vectorSearch(queryVector, limit)
                : chunkIndex.vectorSearch(queryVector, scope, limit);
    }

    private List<RetrievalCandidate> toCandidates(List<IndexSearchHit> hits, boolean keyword) {
        List<RetrievalCandidate> result = new ArrayList<>();
        for (int index = 0; index < hits.size(); index++) {
            result.add(keyword ? RetrievalCandidate.keyword(hits.get(index), index + 1)
                    : RetrievalCandidate.vector(hits.get(index), index + 1));
        }
        return List.copyOf(result);
    }

    private RerankOutcome applyRerank(List<RetrievalCandidate> fused, List<RetrievalCandidate> input,
            List<RerankScore> scores,
            int limit) {
        List<RetrievalCandidate> ranked = scores.stream()
                .filter(score -> score.originalIndex() >= 0 && score.originalIndex() < input.size())
                .sorted(Comparator.comparingInt(RerankScore::rank))
                .map(score -> input.get(score.originalIndex()).withRerank(score.score(), score.rank()))
                .limit(limit)
                .toList();
        if (ranked.isEmpty() && !input.isEmpty()) {
            throw new IllegalStateException("精排服务未返回有效结果");
        }
        List<RetrievalCandidate> finalCandidates = withFinalRanks(ranked, limit);
        Map<String, RetrievalCandidate> annotations = new HashMap<>();
        ranked.forEach(candidate -> annotations.put(candidate.chunkId(), candidate));
        finalCandidates.forEach(candidate -> annotations.put(candidate.chunkId(), candidate));
        List<RetrievalCandidate> traceCandidates = fused.stream()
                .map(candidate -> annotations.getOrDefault(candidate.chunkId(), candidate))
                .toList();
        return new RerankOutcome(traceCandidates, finalCandidates);
    }

    private List<RetrievalCandidate> mergeFinalRanks(List<RetrievalCandidate> fused,
            List<RetrievalCandidate> finalCandidates) {
        Map<String, RetrievalCandidate> selected = new HashMap<>();
        finalCandidates.forEach(candidate -> selected.put(candidate.chunkId(), candidate));
        return fused.stream().map(candidate -> selected.getOrDefault(candidate.chunkId(), candidate)).toList();
    }

    private List<RetrievalCandidate> mergeDedupeTrace(List<RetrievalCandidate> allCandidates,
            List<RetrievalCandidate> annotatedCandidates) {
        Map<String, RetrievalCandidate> annotations = new HashMap<>();
        annotatedCandidates.forEach(candidate -> annotations.put(candidate.chunkId(), candidate));
        return allCandidates.stream()
                .map(candidate -> annotations.getOrDefault(candidate.chunkId(), candidate))
                .toList();
    }

    private List<RetrievalCandidate> withFinalRanks(List<RetrievalCandidate> candidates, int limit) {
        List<RetrievalCandidate> result = new ArrayList<>();
        int size = Math.min(limit, candidates.size());
        for (int index = 0; index < size; index++) {
            result.add(candidates.get(index).withFinalRank(index + 1));
        }
        return List.copyOf(result);
    }

    private String fallback(String stage, RuntimeException exception) {
        String message = exception.getMessage();
        return stage + ": " + (message == null || message.isBlank() ? exception.getClass().getSimpleName() : message);
    }

    private String appendFallback(String existing, String next) {
        return existing == null ? next : existing + "; " + next;
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private record RerankOutcome(List<RetrievalCandidate> traceCandidates,
            List<RetrievalCandidate> finalCandidates) {
    }
}
