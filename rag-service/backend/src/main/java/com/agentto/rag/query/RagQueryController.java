package com.agentto.rag.query;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentto.rag.client.CallerPrincipal;
import com.agentto.rag.client.CallerRequestContext;
import com.agentto.rag.common.api.ApiResponse;
import com.agentto.rag.common.api.BusinessException;
import com.agentto.rag.common.api.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 公共 RAG 查询 REST 控制器。
 *
 * 端点：
 * POST /api/v1/rag/query  执行一次公共 RAG 查询（受 API Key 认证保护）
 *
 * HTTP 状态约定：
 * - 200：查询成功，业务拒答（无知识库、证据不足、引用无效）也返回 200，由 decision 区分
 * - 400：非法参数（query 为空、finalLimit 非法）
 * - 401：API Key 缺失或无效（由 ClientApiKeyInterceptor 处理）
 * - 503：答案生成不可用（未配置 ChatModel）
 *
 * 本控制器只负责取调用方身份、校验参数、调用编排服务并映射响应，
 * 不执行路由、阈值、改写等任何领域逻辑。
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RagQueryController {

    /** finalLimit 缺省值 */
    private static final int DEFAULT_FINAL_LIMIT = 8;

    private final RagQueryService queryService;

    public RagQueryController(RagQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 执行公共 RAG 查询。
     *
     * @param request     查询请求（query 必填，finalLimit 可选）
     * @param httpRequest HTTP 请求（携带认证后的调用方身份）
     * @return 统一响应，data 为查询结果视图
     */
    @PostMapping("/query")
    public ResponseEntity<ApiResponse<RagQueryResponseView>> query(@RequestBody RagQueryRequest request,
            HttpServletRequest httpRequest) {
        // 参数校验：query 必填且非空
        if (request.query() == null || request.query().isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "查询不能为空", HttpStatus.BAD_REQUEST);
        }
        // 参数校验：finalLimit 缺省取默认值，显式提供时须大于 0
        int finalLimit = request.finalLimit() == null ? DEFAULT_FINAL_LIMIT : request.finalLimit();
        if (finalLimit < 1) {
            throw new BusinessException("VALIDATION_ERROR", "finalLimit 必须大于 0", HttpStatus.BAD_REQUEST);
        }

        // 调用方应用 ID 来自认证拦截器写入的请求上下文
        CallerPrincipal principal = CallerRequestContext.principal(httpRequest);
        RagQueryResponse response = queryService.query(
                new RagQueryCommand(principal.appId(), request.query(), finalLimit));
        String traceId = TraceIdFilter.current(httpRequest);

        // 答案生成不可用映射为 503，其余决策（含业务拒答）统一 200
        if (response.decision() == RagQueryDecision.GENERATION_UNAVAILABLE) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiResponse<>("GENERATION_UNAVAILABLE", "答案生成未启用（未配置 ChatModel）",
                            null, traceId));
        }
        return ResponseEntity.ok(ApiResponse.ok(RagQueryResponseView.from(response), traceId));
    }

    /** 公共 RAG 查询请求体 */
    public record RagQueryRequest(String query, Integer finalLimit) {
    }

    /** 公共 RAG 查询响应视图（对外稳定结构，不暴露内部枚举） */
    public record RagQueryResponseView(
            String decision,
            String answer,
            List<CitationView> citations,
            List<QueryAttemptView> attempts,
            String traceUid) {

        /**
         * 从领域响应映射为对外视图。
         *
         * @param response 编排服务返回的领域响应
         * @return 对外视图
         */
        static RagQueryResponseView from(RagQueryResponse response) {
            List<CitationView> citations = response.citations().stream()
                    .map(citation -> new CitationView(citation.chunkId(), citation.quote()))
                    .toList();
            List<QueryAttemptView> attempts = response.attempts().stream()
                    .map(attempt -> new QueryAttemptView(attempt.attemptNo(), attempt.query(),
                            attempt.evidenceDecision().name(), attempt.evidenceCount(), attempt.note()))
                    .toList();
            return new RagQueryResponseView(response.decision().name(), response.answer(),
                    citations, attempts, response.traceUid());
        }
    }

    /** 引用视图 */
    public record CitationView(String chunkId, String quote) {
    }

    /** 单次检索尝试视图 */
    public record QueryAttemptView(int attemptNo, String query, String evidenceDecision,
            int evidenceCount, String note) {
    }
}
