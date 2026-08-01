package com.agentto.rag.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.agentto.rag.citation.CitationValidationResult;
import com.agentto.rag.citation.GeneratedAnswer;
import com.agentto.rag.citation.CitationValidator;
import com.agentto.rag.evaluation.RagFailureCode;
import com.agentto.rag.evidence.EvidenceAssessment;
import com.agentto.rag.evidence.EvidenceDecision;
import com.agentto.rag.evidence.EvidenceGate;
import com.agentto.rag.index.SearchScope;
import com.agentto.rag.observability.ExecutionEvent;
import com.agentto.rag.observability.TechnicalStageDetail;
import com.agentto.rag.retrieval.HybridRetrievalService;
import com.agentto.rag.retrieval.RetrievalCandidate;
import com.agentto.rag.retrieval.RetrievalRequest;
import com.agentto.rag.retrieval.RetrievalResponse;
import com.agentto.rag.rewrite.QueryRewriter;
import com.agentto.rag.routing.KnowledgeBaseRoute;
import com.agentto.rag.routing.KnowledgeBaseRouter;
import com.agentto.rag.routing.RoutingDecision;

/**
 * 公共 RAG 查询编排：路由 → 检索 → 证据门 → 生成 → 引用校验，最多一次改写重试。
 *
 * 有限状态机：
 * <pre>
 * ROUTE
 *   ├─ no route → NO_RELEVANT_KNOWLEDGE_BASE
 *   └─ routed → RETRIEVE_1 → ASSESS_1
 *        ├─ sufficient → GENERATE → VALIDATE
 *        └─ insufficient → REWRITE
 *             ├─ no rewrite → INSUFFICIENT_EVIDENCE
 *             └─ rewritten → RETRIEVE_2 → ASSESS_2
 *                  ├─ insufficient → INSUFFICIENT_EVIDENCE
 *                  └─ sufficient → GENERATE → VALIDATE
 * </pre>
 *
 * 安全约定：
 * - 引用未通过真实性校验时返回 INVALID_CITATION，绝不泄露未验证答案
 * - 未配置 ChatModel 时返回 GENERATION_UNAVAILABLE，绝不拼接伪答案
 * - 模型返回空答案视为证据不足（INSUFFICIENT_EVIDENCE）
 *
 * 每次查询结束时记录编排 Trace（路由画像、选中知识库、证据门、改写、引用校验和阶段事件），
 * Trace 记录失败不影响查询主链路。
 */
@Service
public class RagQueryService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryService.class);

    /** 各检索阶段数量配置（与现有 HybridRetrievalService 调用保持一致） */
    private static final int KEYWORD_LIMIT = 12;
    private static final int VECTOR_LIMIT = 12;
    private static final int FUSION_LIMIT = 10;
    private static final int RERANK_LIMIT = 10;

    private final KnowledgeBaseRouter router;
    private final HybridRetrievalService retrievalService;
    private final EvidenceGate evidenceGate;
    private final QueryRewriter queryRewriter;
    private final AnswerGenerator answerGenerator;
    private final CitationValidator citationValidator;
    private final QueryFlowTraceRecorder flowTraceRecorder;

    /**
     * 构造编排服务。
     *
     * @param router            两阶段知识库路由器
     * @param retrievalService  混合检索服务
     * @param evidenceGate      证据门
     * @param queryRewriter     查询改写器
     * @param answerGenerator   答案生成器
     * @param citationValidator 引用真实性校验器
     * @param flowTraceRecorder 编排 Trace 记录器
     */
    public RagQueryService(KnowledgeBaseRouter router, HybridRetrievalService retrievalService,
            EvidenceGate evidenceGate, QueryRewriter queryRewriter,
            AnswerGenerator answerGenerator, CitationValidator citationValidator,
            QueryFlowTraceRecorder flowTraceRecorder) {
        this.router = router;
        this.retrievalService = retrievalService;
        this.evidenceGate = evidenceGate;
        this.queryRewriter = queryRewriter;
        this.answerGenerator = answerGenerator;
        this.citationValidator = citationValidator;
        this.flowTraceRecorder = flowTraceRecorder;
    }

    /**
     * 执行公共 RAG 查询。
     *
     * @param command 查询命令
     * @return 查询响应
     */
    public RagQueryResponse query(RagQueryCommand command) {
        long startNanos = System.nanoTime();
        FlowTraceCollector collector = new FlowTraceCollector(command);

        // ROUTE：两阶段动态路由
        long routeStartNanos = System.nanoTime();
        KnowledgeBaseRoute route = router.route(command.clientAppId(), command.query());
        collector.route(route, elapsedMs(routeStartNanos));
        if (route.decision() != RoutingDecision.ROUTED) {
            return finish(collector,
                    refusal(RagQueryDecision.NO_RELEVANT_KNOWLEDGE_BASE, List.of(), null,
                            "路由阶段无相关知识库"),
                    startNanos);
        }
        // 检索范围限定在路由选中的知识库内（多租户隔离边界）
        SearchScope scope = new SearchScope(Set.copyOf(route.selectedKnowledgeBaseIds()));

        // RETRIEVE_1 + ASSESS_1：首次检索并评估证据
        AttemptResult first = executeAttempt(command.query(), route, scope, 1, command.finalLimit(),
                command.clientAppId(), collector);
        List<QueryAttempt> attempts = new ArrayList<>();
        attempts.add(first.attempt());
        if (first.assessment().decision() == EvidenceDecision.SUFFICIENT) {
            return finish(collector, generateAndValidate(collector, first, attempts), startNanos);
        }

        // REWRITE：证据不足时最多改写一次；改写器异常视为改写不可用，不重试
        long rewriteStartNanos = System.nanoTime();
        Optional<String> rewritten;
        try {
            rewritten = queryRewriter.rewrite(command.query(), first.assessment().reason());
        } catch (RuntimeException e) {
            rewritten = Optional.empty();
        }
        collector.rewrite(rewritten, elapsedMs(rewriteStartNanos));
        if (rewritten.isEmpty()) {
            return finish(collector,
                    refusal(RagQueryDecision.INSUFFICIENT_EVIDENCE, attempts, first.traceUid(),
                            "首次证据不足且改写不可用"),
                    startNanos);
        }

        // RETRIEVE_2 + ASSESS_2：改写后二次检索
        AttemptResult second = executeAttempt(rewritten.get(), route, scope, 2, command.finalLimit(),
                command.clientAppId(), collector);
        attempts.add(second.attempt());
        if (second.assessment().decision() != EvidenceDecision.SUFFICIENT) {
            return finish(collector,
                    refusal(RagQueryDecision.INSUFFICIENT_EVIDENCE, attempts, second.traceUid(),
                            "二次检索证据仍不足"),
                    startNanos);
        }
        return finish(collector, generateAndValidate(collector, second, attempts), startNanos);
    }

    /**
     * 执行单次检索尝试并评估证据。
     *
     * @param query      本次实际查询
     * @param route      路由结果（已确认 ROUTED）
     * @param scope      知识库检索范围
     * @param attemptNo  尝试序号
     * @param finalLimit 最终返回数量
     * @param collector  编排 Trace 收集器
     * @return 尝试结果（快照、评估、Trace ID）
     */
    private AttemptResult executeAttempt(String query, KnowledgeBaseRoute route, SearchScope scope,
            int attemptNo, int finalLimit, Long clientAppId, FlowTraceCollector collector) {
        long startNanos = System.nanoTime();
        RetrievalRequest request = new RetrievalRequest(query, KEYWORD_LIMIT, VECTOR_LIMIT,
                FUSION_LIMIT, RERANK_LIMIT, finalLimit)
                .withScope(scope)
                .withAttemptNo(attemptNo)
                .withRequesterId(clientAppId);
        RetrievalResponse response = retrievalService.search(request);
        EvidenceAssessment assessment = evidenceGate.assess(route, response.candidates());
        collector.evidenceGate(attemptNo, assessment, response.candidates().size(),
                response.traceUid(), elapsedMs(startNanos));
        QueryAttempt attempt = new QueryAttempt(attemptNo, query, assessment.decision(),
                response.candidates().size(), assessment.reason());
        return new AttemptResult(query, attempt, assessment, response.traceUid(), response.candidates());
    }

    /**
     * 生成答案并校验引用真实性。
     *
     * @param collector 编排 Trace 收集器
     * @param attempt   证据充足的检索尝试
     * @param attempts  累计尝试快照（供响应输出）
     * @return 查询响应
     */
    private RagQueryResponse generateAndValidate(FlowTraceCollector collector, AttemptResult attempt,
            List<QueryAttempt> attempts) {
        // GENERATE：答案生成（未配置 ChatModel 时抛 GenerationUnavailableException）
        GeneratedAnswer answer;
        try {
            answer = answerGenerator.generate(attempt.query(), attempt.evidence());
        } catch (GenerationUnavailableException e) {
            return refusal(RagQueryDecision.GENERATION_UNAVAILABLE, attempts, attempt.traceUid(),
                    "答案生成未启用（未配置 ChatModel）");
        }

        // VALIDATE：引用真实性校验，不通过时拒绝输出，防止泄露不可信答案
        long validateStartNanos = System.nanoTime();
        CitationValidationResult validation = citationValidator.validate(answer, attempt.evidence());
        collector.citationValidate(validation, elapsedMs(validateStartNanos));
        if (!validation.valid()) {
            return refusal(RagQueryDecision.INVALID_CITATION, attempts, attempt.traceUid(),
                    validation.reason());
        }

        // 模型返回空答案：视为模型判定证据不足
        if (answer.text() == null || answer.text().isBlank()) {
            return refusal(RagQueryDecision.INSUFFICIENT_EVIDENCE, attempts, attempt.traceUid(),
                    "模型判定证据不足，未生成答案");
        }

        return new RagQueryResponse(RagQueryDecision.ANSWERED, answer.text(),
                validation.validCitations(), List.copyOf(attempts), attempt.traceUid());
    }

    /**
     * 构造拒答响应：不携带答案和引用。
     *
     * @param decision  拒答决策
     * @param attempts  累计尝试快照
     * @param traceUid  Trace ID
     * @param reason    拒答原因（仅诊断，不放入响应正文）
     * @return 查询响应
     */
    private RagQueryResponse refusal(RagQueryDecision decision, List<QueryAttempt> attempts,
            String traceUid, String reason) {
        return new RagQueryResponse(decision, null, List.of(), attempts, traceUid);
    }

    /**
     * 结束编排：补充最终字段、记录 Trace 并返回响应。
     * Trace 记录失败只记日志，不影响查询结果。
     */
    private RagQueryResponse finish(FlowTraceCollector collector, RagQueryResponse response,
            long startNanos) {
        collector.complete(response, elapsedMs(startNanos));
        try {
            flowTraceRecorder.record(collector.toTrace());
        } catch (RuntimeException exception) {
            log.warn("公共查询 Trace 记录失败，已忽略：{}", exception.getMessage());
        }
        return response;
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** 单次尝试的内部结果：本次查询、快照、评估、Trace ID 和证据候选 */
    private record AttemptResult(String query, QueryAttempt attempt, EvidenceAssessment assessment,
            String traceUid, List<RetrievalCandidate> evidence) {
    }

    /**
     * 编排 Trace 收集器。
     *
     * <p>在各阶段事件发生时累积信息，结束时构造 {@link QueryFlowTrace} 快照。
     */
    private final class FlowTraceCollector {

        private final Long clientAppId;
        private final String originalQuery;
        private final int finalLimit;
        private final List<String> traceUids = new ArrayList<>();
        private final List<ExecutionEvent> events = new ArrayList<>();

        private RoutingDecision routingDecision = RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE;
        private List<Long> profileShortlist = List.of();
        private List<QueryFlowTrace.KnowledgeBaseSelection> selected = List.of();
        private EvidenceDecision evidenceDecision;
        private boolean rewriteAttempted;
        private Boolean citationValid;
        private String effectiveQuery;
        private RagQueryDecision decision;
        private RagFailureCode failureCode;
        private int answerLength;
        private long totalMs;

        FlowTraceCollector(RagQueryCommand command) {
            this.clientAppId = command.clientAppId();
            this.originalQuery = command.query();
            this.finalLimit = command.finalLimit();
        }

        /** 路由阶段：记录画像召回与内容验证结果 */
        void route(KnowledgeBaseRoute route, long elapsedMs) {
            this.routingDecision = route.decision();
            this.profileShortlist = route.profileShortlist();
            this.selected = route.verificationScores().entrySet().stream()
                    .map(entry -> new QueryFlowTrace.KnowledgeBaseSelection(entry.getKey(),
                            entry.getValue()))
                    .toList();
            events.add(event(QueryFlowStage.ROUTE_PROFILE, "COMPLETED", elapsedMs,
                    new TechnicalStageDetail("画像召回 Top " + profileShortlist.size(),
                            null, profileShortlist.size(), null, null, null, null)));
            events.add(event(QueryFlowStage.ROUTE_VERIFY, "COMPLETED", 0,
                    new TechnicalStageDetail("内容验证选中 Top " + selected.size(),
                            profileShortlist.size(), selected.size(), null, null, null, null)));
        }

        /** 证据门阶段：每次检索尝试后记录评估结果 */
        void evidenceGate(int attemptNo, EvidenceAssessment assessment, int candidateCount,
                String traceUid, long elapsedMs) {
            this.evidenceDecision = assessment.decision();
            if (traceUid != null) {
                this.traceUids.add(traceUid);
            }
            events.add(event(QueryFlowStage.EVIDENCE_GATE, "COMPLETED", elapsedMs,
                    new TechnicalStageDetail("第 " + attemptNo + " 次尝试证据门：" + assessment.reason(),
                            candidateCount, assessment.qualifyingEvidenceCount(), null,
                            Map.of("topScore", assessment.topScore()), null, null)));
        }

        /** 改写阶段：记录改写是否有效 */
        void rewrite(Optional<String> rewritten, long elapsedMs) {
            this.rewriteAttempted = true;
            String summary = rewritten.isPresent() ? "改写有效：" + abbreviate(rewritten.get(), 100)
                    : "改写不可用或无实质变化";
            events.add(event(QueryFlowStage.QUERY_REWRITE,
                    rewritten.isPresent() ? "COMPLETED" : "SKIPPED", elapsedMs,
                    new TechnicalStageDetail(summary, 1,
                            rewritten.isPresent() ? 1 : 0, null, null, null, null)));
        }

        /** 引用校验阶段：记录校验结果 */
        void citationValidate(CitationValidationResult validation, long elapsedMs) {
            this.citationValid = validation.valid();
            events.add(event(QueryFlowStage.CITATION_VALIDATE,
                    validation.valid() ? "COMPLETED" : "FAILED", elapsedMs,
                    new TechnicalStageDetail(validation.reason(), null,
                            validation.validCitations().size(), null, null, null, null)));
        }

        /** 编排结束：补充最终字段与 COMPLETE 事件 */
        void complete(RagQueryResponse response, long elapsedMs) {
            this.decision = response.decision();
            this.totalMs = elapsedMs;
            this.answerLength = response.answer() == null ? 0 : response.answer().length();
            // 生效查询取最后一次尝试的实际查询
            if (!response.attempts().isEmpty()) {
                this.effectiveQuery = response.attempts().get(response.attempts().size() - 1).query();
            } else {
                this.effectiveQuery = this.originalQuery;
            }
            this.failureCode = switch (response.decision()) {
                case GENERATION_UNAVAILABLE -> RagFailureCode.MODEL_FAILURE;
                case INVALID_CITATION -> RagFailureCode.INVALID_CITATION;
                default -> null;
            };
            events.add(event(QueryFlowStage.COMPLETE, "COMPLETED", 0,
                    new TechnicalStageDetail("编排结束，决策=" + response.decision().name(),
                            null, null, null, null, null, null)));
        }

        /** 构造编排 Trace 快照 */
        QueryFlowTrace toTrace() {
            return new QueryFlowTrace(clientAppId, originalQuery,
                    effectiveQuery == null ? originalQuery : effectiveQuery,
                    finalLimit, decision, routingDecision, profileShortlist, selected,
                    evidenceDecision, rewriteAttempted, citationValid, failureCode,
                    traceUids.size(), List.copyOf(traceUids), List.copyOf(events),
                    answerLength, totalMs);
        }

        /** 构造阶段事件 */
        private ExecutionEvent event(QueryFlowStage stage, String status, long elapsedMs,
                TechnicalStageDetail detail) {
            Instant now = Instant.now();
            return new ExecutionEvent(stage.name(), status, now.minusMillis(elapsedMs).toString(),
                    now.toString(), elapsedMs, detail);
        }

        private String abbreviate(String value, int max) {
            return value.length() <= max ? value : value.substring(0, max);
        }
    }
}
