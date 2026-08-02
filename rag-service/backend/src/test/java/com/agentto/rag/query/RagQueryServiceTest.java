package com.agentto.rag.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentto.rag.citation.Citation;
import com.agentto.rag.citation.CitationValidator;
import com.agentto.rag.citation.GeneratedAnswer;
import com.agentto.rag.evaluation.RagFailureCode;
import com.agentto.rag.evidence.EvidenceGate;
import com.agentto.rag.evidence.EvidencePolicyProperties;
import com.agentto.rag.index.SearchScope;
import com.agentto.rag.observability.ExecutionEvent;
import com.agentto.rag.retrieval.DedupeStatus;
import com.agentto.rag.retrieval.HybridRetrievalService;
import com.agentto.rag.retrieval.RetrievalCandidate;
import com.agentto.rag.retrieval.RetrievalRequest;
import com.agentto.rag.retrieval.RetrievalResponse;
import com.agentto.rag.retrieval.RetrievalTimings;
import com.agentto.rag.rewrite.QueryRewriter;
import com.agentto.rag.routing.KnowledgeBaseRoute;
import com.agentto.rag.routing.KnowledgeBaseRouter;
import com.agentto.rag.routing.RoutingDecision;

/**
 * 公共 RAG 查询编排测试。
 * 覆盖主路径（路由→检索→生成→校验）、无路由、证据不足改写重试、
 * 二次仍不足、改写器异常/不可用、引用无效、生成不可用和空答案等状态机分支。
 */
class RagQueryServiceTest {

    private static final SearchScope SCOPE = new SearchScope(Set.of(101L, 102L));

    private StubRouter router;
    private StubRetrieval retrieval;
    private StubQueryRewriter rewriter;
    private StubAnswerGenerator generator;
    private StubFlowTraceRecorder traceRecorder;
    private RagQueryService service;

    @BeforeEach
    void setUp() {
        router = new StubRouter(routedRoute());
        retrieval = new StubRetrieval(List.of(
                response("trace-1", List.of(highCandidate("c1", "预算审批分为三步。"),
                        highCandidate("c2", "单笔超五十万需总经理审批。")))));
        rewriter = new StubQueryRewriter(() -> Optional.of("预算审批流程"));
        generator = new StubAnswerGenerator(() -> new GeneratedAnswer("预算审批分为三步。",
                List.of(new Citation("c1", "预算审批分为三步"))));
        traceRecorder = new StubFlowTraceRecorder();
        service = newService();
    }

    /** 主路径：路由 → 检索 → 证据门通过 → 生成 → 引用校验通过 → ANSWERED */
    @Test
    void routesRetrievesGeneratesAndValidatesAnAnswer() {
        RagQueryResponse response = service.query(new RagQueryCommand(10L, "预算如何审批", 8));

        assertThat(response.decision()).isEqualTo(RagQueryDecision.ANSWERED);
        assertThat(response.answer()).isEqualTo("预算审批分为三步。");
        assertThat(response.citations()).extracting(Citation::chunkId).containsExactly("c1");
        assertThat(response.attempts()).hasSize(1);
        assertThat(response.traceUid()).isEqualTo("trace-1");
        // 检索请求携带知识库范围且为首次尝试
        assertThat(retrieval.requests()).hasSize(1);
        assertThat(retrieval.requests().get(0).scope()).isEqualTo(SCOPE);
        assertThat(retrieval.requests().get(0).attemptNo()).isEqualTo(1);
        // 生成器收到的是路由后的查询
        assertThat(generator.receivedQueries()).containsExactly("预算如何审批");
        // 编排 Trace：决策、调用方、生效查询、路由画像和阶段事件完整
        assertThat(traceRecorder.records()).hasSize(1);
        QueryFlowTrace trace = traceRecorder.records().get(0);
        assertThat(trace.clientAppId()).isEqualTo(10L);
        assertThat(trace.decision()).isEqualTo(RagQueryDecision.ANSWERED);
        assertThat(trace.routingDecision()).isEqualTo(RoutingDecision.ROUTED);
        assertThat(trace.effectiveQuery()).isEqualTo("预算如何审批");
        assertThat(trace.profileShortlist()).containsExactly(101L, 102L);
        assertThat(trace.selectedKnowledgeBases()).hasSize(2);
        assertThat(trace.attemptCount()).isEqualTo(1);
        assertThat(trace.traceUids()).containsExactly("trace-1");
        assertThat(trace.citationValid()).isTrue();
        assertThat(trace.rewriteAttempted()).isFalse();
        assertThat(trace.failureCode()).isNull();
        assertThat(trace.events()).extracting(ExecutionEvent::stage).contains(
                QueryFlowStage.ROUTE_PROFILE.name(), QueryFlowStage.ROUTE_VERIFY.name(),
                QueryFlowStage.EVIDENCE_GATE.name(), QueryFlowStage.CITATION_VALIDATE.name(),
                QueryFlowStage.COMPLETE.name());
        assertThat(trace.totalMs()).isGreaterThanOrEqualTo(0);
    }

    /** 路由无结果：不调用检索、改写、生成，直接拒答 */
    @Test
    void returnsNoRelevantKnowledgeBaseWithoutTouchingDownstream() {
        router = new StubRouter(new KnowledgeBaseRoute(RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE,
                List.of(), List.of(), Map.of()));
        service = newService();

        RagQueryResponse response = service.query(new RagQueryCommand(10L, "无关问题", 8));

        assertThat(response.decision()).isEqualTo(RagQueryDecision.NO_RELEVANT_KNOWLEDGE_BASE);
        assertThat(response.answer()).isNull();
        assertThat(retrieval.requests()).isEmpty();
        assertThat(rewriter.receivedQueries()).isEmpty();
        assertThat(generator.receivedQueries()).isEmpty();
        // 编排 Trace：记录路由拒答，无检索/改写/校验阶段
        assertThat(traceRecorder.records()).hasSize(1);
        QueryFlowTrace trace = traceRecorder.records().get(0);
        assertThat(trace.decision()).isEqualTo(RagQueryDecision.NO_RELEVANT_KNOWLEDGE_BASE);
        assertThat(trace.routingDecision()).isEqualTo(RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE);
        assertThat(trace.attemptCount()).isZero();
        assertThat(trace.evidenceDecision()).isNull();
        assertThat(trace.events()).extracting(ExecutionEvent::stage)
                .doesNotContain(QueryFlowStage.EVIDENCE_GATE.name());
    }

    /** 首次证据不足 + 改写有效：只重试一次，第二次用改写后的查询 */
    @Test
    void rewritesAndRetriesExactlyOnceWhenFirstAttemptInsufficient() {
        retrieval = new StubRetrieval(List.of(
                response("trace-1", List.of(lowCandidate("c1", "低分内容"), lowCandidate("c2", "低分内容"))),
                response("trace-2", List.of(highCandidate("c1", "预算审批分为三步。"),
                        highCandidate("c2", "单笔超五十万需总经理审批。")))));
        service = newService();

        RagQueryResponse response = service.query(new RagQueryCommand(10L, "预算如何审批", 8));

        assertThat(response.decision()).isEqualTo(RagQueryDecision.ANSWERED);
        assertThat(response.traceUid()).isEqualTo("trace-2");
        assertThat(response.attempts()).hasSize(2);
        // 改写器收到原始查询和首次拒答原因
        assertThat(rewriter.receivedQueries()).containsExactly("预算如何审批");
        // 第二次检索使用改写后的查询，attemptNo=2
        assertThat(retrieval.requests()).hasSize(2);
        assertThat(retrieval.requests().get(1).query()).isEqualTo("预算审批流程");
        assertThat(retrieval.requests().get(1).attemptNo()).isEqualTo(2);
        // 生成器只对第二次证据生成
        assertThat(generator.receivedQueries()).containsExactly("预算审批流程");
        assertThat(response.attempts().get(0).attemptNo()).isEqualTo(1);
        assertThat(response.attempts().get(1).attemptNo()).isEqualTo(2);
        // 编排 Trace：两次尝试、改写生效、生效查询为改写后查询
        assertThat(traceRecorder.records()).hasSize(1);
        QueryFlowTrace trace = traceRecorder.records().get(0);
        assertThat(trace.attemptCount()).isEqualTo(2);
        assertThat(trace.traceUids()).containsExactly("trace-1", "trace-2");
        assertThat(trace.rewriteAttempted()).isTrue();
        assertThat(trace.effectiveQuery()).isEqualTo("预算审批流程");
        assertThat(trace.events()).extracting(ExecutionEvent::stage)
                .contains(QueryFlowStage.QUERY_REWRITE.name());
    }

    /** 二次检索证据仍不足：返回 INSUFFICIENT_EVIDENCE，不调用生成器 */
    @Test
    void returnsInsufficientWhenSecondAttemptStillInsufficient() {
        retrieval = new StubRetrieval(List.of(
                response("trace-1", List.of(lowCandidate("c1", "低分内容"), lowCandidate("c2", "低分内容"))),
                response("trace-2", List.of(lowCandidate("c3", "低分内容"), lowCandidate("c4", "低分内容")))));
        service = newService();

        RagQueryResponse response = service.query(new RagQueryCommand(10L, "预算如何审批", 8));

        assertThat(response.decision()).isEqualTo(RagQueryDecision.INSUFFICIENT_EVIDENCE);
        assertThat(response.answer()).isNull();
        assertThat(response.attempts()).hasSize(2);
        assertThat(generator.receivedQueries()).isEmpty();
    }

    /** 改写器返回空（不可用）：不重试，返回首次拒答 */
    @Test
    void returnsFirstRefusalWhenRewriteUnavailable() {
        rewriter = new StubQueryRewriter(() -> Optional.empty());
        retrieval = new StubRetrieval(List.of(
                response("trace-1", List.of(lowCandidate("c1", "低分内容"), lowCandidate("c2", "低分内容")))));
        service = newService();

        RagQueryResponse response = service.query(new RagQueryCommand(10L, "预算如何审批", 8));

        assertThat(response.decision()).isEqualTo(RagQueryDecision.INSUFFICIENT_EVIDENCE);
        assertThat(response.attempts()).hasSize(1);
        assertThat(retrieval.requests()).hasSize(1);
        assertThat(generator.receivedQueries()).isEmpty();
    }

    /** 改写器异常：不重试，返回首次拒答 */
    @Test
    void returnsFirstRefusalWhenRewriterThrows() {
        rewriter = new StubQueryRewriter(() -> {
            throw new IllegalStateException("rewriter down");
        });
        retrieval = new StubRetrieval(List.of(
                response("trace-1", List.of(lowCandidate("c1", "低分内容"), lowCandidate("c2", "低分内容")))));
        service = newService();

        RagQueryResponse response = service.query(new RagQueryCommand(10L, "预算如何审批", 8));

        assertThat(response.decision()).isEqualTo(RagQueryDecision.INSUFFICIENT_EVIDENCE);
        assertThat(response.attempts()).hasSize(1);
        assertThat(retrieval.requests()).hasSize(1);
    }

    /** 引用未通过真实性校验：返回 INVALID_CITATION，不泄露未验证答案 */
    @Test
    void rejectsInvalidCitationsWithoutLeakingAnswer() {
        generator = new StubAnswerGenerator(() -> new GeneratedAnswer("编造的答案",
                List.of(new Citation("invented", "不存在的原文"))));
        service = newService();

        RagQueryResponse response = service.query(new RagQueryCommand(10L, "预算如何审批", 8));

        assertThat(response.decision()).isEqualTo(RagQueryDecision.INVALID_CITATION);
        assertThat(response.answer()).isNull();
        assertThat(response.citations()).isEmpty();
    }

    /** 未配置 ChatModel：返回 GENERATION_UNAVAILABLE，不拼接伪答案 */
    @Test
    void returnsGenerationUnavailableWhenNoChatModel() {
        generator = new StubAnswerGenerator(() -> {
            throw new GenerationUnavailableException("答案生成未启用");
        });
        service = newService();

        RagQueryResponse response = service.query(new RagQueryCommand(10L, "预算如何审批", 8));

        assertThat(response.decision()).isEqualTo(RagQueryDecision.GENERATION_UNAVAILABLE);
        assertThat(response.answer()).isNull();
        assertThat(response.citations()).isEmpty();
        // 编排 Trace：故障码为 MODEL_FAILURE
        assertThat(traceRecorder.records()).hasSize(1);
        assertThat(traceRecorder.records().get(0).failureCode()).isEqualTo(RagFailureCode.MODEL_FAILURE);
    }

    /** 模型返回空答案：视为模型判定证据不足 */
    @Test
    void treatsEmptyModelAnswerAsInsufficientEvidence() {
        generator = new StubAnswerGenerator(() -> new GeneratedAnswer("", List.of()));
        service = newService();

        RagQueryResponse response = service.query(new RagQueryCommand(10L, "预算如何审批", 8));

        assertThat(response.decision()).isEqualTo(RagQueryDecision.INSUFFICIENT_EVIDENCE);
        assertThat(response.answer()).isNull();
    }

    /** 命令缺少调用方身份时必须在进入编排前拒绝。 */
    @Test
    void rejectsNullClientAppId() {
        assertThatThrownBy(() -> new RagQueryCommand(null, "预算审批", 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("clientAppId 不能为空");
    }

    /** 命令查询为空白时必须在进入编排前拒绝。 */
    @Test
    void rejectsBlankQuery() {
        assertThatThrownBy(() -> new RagQueryCommand(1L, " ", 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("查询不能为空");
    }

    /** 命令最终证据数量必须为正数。 */
    @Test
    void rejectsNonPositiveFinalLimit() {
        assertThatThrownBy(() -> new RagQueryCommand(1L, "预算审批", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finalLimit 必须大于 0");
    }

    // --- 辅助方法 ---

    /** 构造编排服务 */
    private RagQueryService newService() {
        return new RagQueryService(router, retrieval,
                new EvidenceGate(new EvidencePolicyProperties()), rewriter,
                generator, new CitationValidator(), traceRecorder);
    }

    /** 构造已路由的默认路由结果 */
    private KnowledgeBaseRoute routedRoute() {
        return new KnowledgeBaseRoute(RoutingDecision.ROUTED,
                List.of(101L, 102L), List.of(101L, 102L), Map.of(101L, 0.8, 102L, 0.7));
    }

    /** 构造检索响应 */
    private RetrievalResponse response(String traceUid, List<RetrievalCandidate> candidates) {
        return new RetrievalResponse(traceUid, candidates, null,
                new RetrievalTimings(0, 0, 0, 0, 0, 0));
    }

    /** 构造高分数候选（rerank=0.9 超过 0.55 阈值） */
    private RetrievalCandidate highCandidate(String chunkId, String content) {
        return new RetrievalCandidate(chunkId, content, null, null, null, null, Map.of(),
                null, null, null, null, 0.9, null, 0.9, null, null,
                null, DedupeStatus.KEPT, null);
    }

    /** 构造低分数候选（rerank=0.1 低于 0.55 阈值） */
    private RetrievalCandidate lowCandidate(String chunkId, String content) {
        return new RetrievalCandidate(chunkId, content, null, null, null, null, Map.of(),
                null, null, null, null, 0.1, null, 0.1, null, null,
                null, DedupeStatus.KEPT, null);
    }

    /** 桩路由器：返回预置路由 */
    static final class StubRouter extends KnowledgeBaseRouter {

        private final KnowledgeBaseRoute route;

        StubRouter(KnowledgeBaseRoute route) {
            super(null, null, null, null, null);
            this.route = route;
        }

        @Override
        public KnowledgeBaseRoute route(Long clientAppId, String query) {
            return route;
        }
    }

    /** 桩检索服务：按调用顺序返回预置响应并记录请求 */
    static final class StubRetrieval extends HybridRetrievalService {

        private final List<RetrievalResponse> responses;
        private final List<RetrievalRequest> requests = new ArrayList<>();

        StubRetrieval(List<RetrievalResponse> responses) {
            super(null, null, null, null);
            this.responses = responses;
        }

        /** 返回收到的检索请求列表（测试断言用） */
        List<RetrievalRequest> requests() {
            return requests;
        }

        @Override
        public RetrievalResponse search(RetrievalRequest request) {
            requests.add(request);
            int index = Math.min(requests.size() - 1, responses.size() - 1);
            return responses.get(index);
        }
    }

    /** 桩改写器：按响应器返回结果，响应器可抛异常模拟故障 */
    static final class StubQueryRewriter implements QueryRewriter {

        private final Supplier<Optional<String>> responder;
        private final List<String> receivedQueries = new ArrayList<>();

        StubQueryRewriter(Supplier<Optional<String>> responder) {
            this.responder = responder;
        }

        /** 返回收到的原始查询列表（测试断言用） */
        List<String> receivedQueries() {
            return receivedQueries;
        }

        @Override
        public Optional<String> rewrite(String originalQuery, String failureReason) {
            receivedQueries.add(originalQuery);
            return responder.get();
        }
    }

    /** 桩答案生成器：按响应器返回答案，响应器可抛 GenerationUnavailableException */
    static final class StubAnswerGenerator implements AnswerGenerator {

        private final Supplier<GeneratedAnswer> responder;
        private final List<String> receivedQueries = new ArrayList<>();

        StubAnswerGenerator(Supplier<GeneratedAnswer> responder) {
            this.responder = responder;
        }

        /** 返回收到的查询列表（测试断言用） */
        List<String> receivedQueries() {
            return receivedQueries;
        }

        @Override
        public GeneratedAnswer generate(String query, List<RetrievalCandidate> evidence) {
            receivedQueries.add(query);
            return responder.get();
        }
    }

    /** 桩编排 Trace 记录器：保存全部记录供测试断言 */
    static final class StubFlowTraceRecorder implements QueryFlowTraceRecorder {

        private final List<QueryFlowTrace> records = new ArrayList<>();

        /** 返回全部收到的编排 Trace（测试断言用） */
        List<QueryFlowTrace> records() {
            return records;
        }

        @Override
        public void record(QueryFlowTrace trace) {
            records.add(trace);
        }
    }
}
