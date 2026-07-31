package com.agentto.rag.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.agentto.rag.citation.CitationValidationResult;
import com.agentto.rag.citation.GeneratedAnswer;
import com.agentto.rag.citation.CitationValidator;
import com.agentto.rag.evidence.EvidenceAssessment;
import com.agentto.rag.evidence.EvidenceDecision;
import com.agentto.rag.evidence.EvidenceGate;
import com.agentto.rag.index.SearchScope;
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
 */
@Service
public class RagQueryService {

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

    /**
     * 构造编排服务。
     *
     * @param router            两阶段知识库路由器
     * @param retrievalService  混合检索服务
     * @param evidenceGate      证据门
     * @param queryRewriter     查询改写器
     * @param answerGenerator   答案生成器
     * @param citationValidator 引用真实性校验器
     */
    public RagQueryService(KnowledgeBaseRouter router, HybridRetrievalService retrievalService,
            EvidenceGate evidenceGate, QueryRewriter queryRewriter,
            AnswerGenerator answerGenerator, CitationValidator citationValidator) {
        this.router = router;
        this.retrievalService = retrievalService;
        this.evidenceGate = evidenceGate;
        this.queryRewriter = queryRewriter;
        this.answerGenerator = answerGenerator;
        this.citationValidator = citationValidator;
    }

    /**
     * 执行公共 RAG 查询。
     *
     * @param command 查询命令
     * @return 查询响应
     */
    public RagQueryResponse query(RagQueryCommand command) {
        // ROUTE：两阶段动态路由
        KnowledgeBaseRoute route = router.route(command.clientAppId(), command.query());
        if (route.decision() != RoutingDecision.ROUTED) {
            return refusal(RagQueryDecision.NO_RELEVANT_KNOWLEDGE_BASE, List.of(), null,
                    "路由阶段无相关知识库");
        }
        // 检索范围限定在路由选中的知识库内（多租户隔离边界）
        SearchScope scope = new SearchScope(Set.copyOf(route.selectedKnowledgeBaseIds()));

        // RETRIEVE_1 + ASSESS_1：首次检索并评估证据
        AttemptResult first = executeAttempt(command.query(), route, scope, 1, command.finalLimit());
        List<QueryAttempt> attempts = new ArrayList<>();
        attempts.add(first.attempt());
        if (first.assessment().decision() == EvidenceDecision.SUFFICIENT) {
            return generateAndValidate(first, attempts);
        }

        // REWRITE：证据不足时最多改写一次；改写器异常视为改写不可用，不重试
        Optional<String> rewritten;
        try {
            rewritten = queryRewriter.rewrite(command.query(), first.assessment().reason());
        } catch (RuntimeException e) {
            rewritten = Optional.empty();
        }
        if (rewritten.isEmpty()) {
            return refusal(RagQueryDecision.INSUFFICIENT_EVIDENCE, attempts, first.traceUid(),
                    "首次证据不足且改写不可用");
        }

        // RETRIEVE_2 + ASSESS_2：改写后二次检索
        AttemptResult second = executeAttempt(rewritten.get(), route, scope, 2, command.finalLimit());
        attempts.add(second.attempt());
        if (second.assessment().decision() != EvidenceDecision.SUFFICIENT) {
            return refusal(RagQueryDecision.INSUFFICIENT_EVIDENCE, attempts, second.traceUid(),
                    "二次检索证据仍不足");
        }
        return generateAndValidate(second, attempts);
    }

    /**
     * 执行单次检索尝试并评估证据。
     *
     * @param query     本次实际查询
     * @param route     路由结果（已确认 ROUTED）
     * @param scope     知识库检索范围
     * @param attemptNo 尝试序号
     * @param finalLimit 最终返回数量
     * @return 尝试结果（快照、评估、Trace ID）
     */
    private AttemptResult executeAttempt(String query, KnowledgeBaseRoute route, SearchScope scope,
            int attemptNo, int finalLimit) {
        RetrievalRequest request = new RetrievalRequest(query, KEYWORD_LIMIT, VECTOR_LIMIT,
                FUSION_LIMIT, RERANK_LIMIT, finalLimit)
                .withScope(scope)
                .withAttemptNo(attemptNo);
        RetrievalResponse response = retrievalService.search(request);
        EvidenceAssessment assessment = evidenceGate.assess(route, response.candidates());
        QueryAttempt attempt = new QueryAttempt(attemptNo, query, assessment.decision(),
                response.candidates().size(), assessment.reason());
        return new AttemptResult(query, attempt, assessment, response.traceUid(), response.candidates());
    }

    /**
     * 生成答案并校验引用真实性。
     *
     * @param attempt  证据充足的检索尝试
     * @param attempts 累计尝试快照（供响应输出）
     * @return 查询响应
     */
    private RagQueryResponse generateAndValidate(AttemptResult attempt, List<QueryAttempt> attempts) {
        // GENERATE：答案生成（未配置 ChatModel 时抛 GenerationUnavailableException）
        GeneratedAnswer answer;
        try {
            answer = answerGenerator.generate(attempt.query(), attempt.evidence());
        } catch (GenerationUnavailableException e) {
            return refusal(RagQueryDecision.GENERATION_UNAVAILABLE, attempts, attempt.traceUid(),
                    "答案生成未启用（未配置 ChatModel）");
        }

        // VALIDATE：引用真实性校验，不通过时拒绝输出，防止泄露不可信答案
        CitationValidationResult validation = citationValidator.validate(answer, attempt.evidence());
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

    /** 单次尝试的内部结果：本次查询、快照、评估、Trace ID 和证据候选 */
    private record AttemptResult(String query, QueryAttempt attempt, EvidenceAssessment assessment,
            String traceUid, List<RetrievalCandidate> evidence) {
    }
}
