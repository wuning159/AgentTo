package com.agentto.rag.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.agentto.rag.citation.Citation;
import com.agentto.rag.client.CallerPrincipal;
import com.agentto.rag.common.api.ApiResponse;
import com.agentto.rag.common.api.BusinessException;
import com.agentto.rag.evidence.EvidenceDecision;
import com.agentto.rag.query.RagQueryController.RagQueryRequest;
import com.agentto.rag.query.RagQueryController.RagQueryResponseView;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 公共 RAG 查询控制器测试。
 * 覆盖参数校验、决策到 HTTP 状态映射、DTO 映射和调用方身份透传。
 */
@ExtendWith(MockitoExtension.class)
class RagQueryControllerTest {

    @Mock
    private RagQueryService queryService;

    private RagQueryController controller;

    @BeforeEach
    void setUp() {
        controller = new RagQueryController(queryService);
    }

    /** 有效请求：ANSWERED 返回 200，并完整映射决策、答案、引用、尝试快照和 Trace ID */
    @Test
    void answersWith200WhenAnswered() {
        RagQueryResponse domain = new RagQueryResponse(RagQueryDecision.ANSWERED, "预算审批分为三步。",
                List.of(new Citation("c1", "预算审批分为三步")),
                List.of(new QueryAttempt(1, "预算如何审批", EvidenceDecision.SUFFICIENT, 2, "证据充足")),
                "trace-1");
        when(queryService.query(any())).thenReturn(domain);

        ResponseEntity<ApiResponse<RagQueryResponseView>> result = controller.query(
                new RagQueryRequest("预算如何审批", 8), request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        RagQueryResponseView view = result.getBody().data();
        assertThat(view.decision()).isEqualTo("ANSWERED");
        assertThat(view.answer()).isEqualTo("预算审批分为三步。");
        assertThat(view.citations()).hasSize(1);
        assertThat(view.citations().get(0).chunkId()).isEqualTo("c1");
        assertThat(view.citations().get(0).quote()).isEqualTo("预算审批分为三步");
        assertThat(view.attempts()).hasSize(1);
        assertThat(view.attempts().get(0).attemptNo()).isEqualTo(1);
        assertThat(view.traceUid()).isEqualTo("trace-1");
        // 服务收到调用方应用 ID 和原始查询
        verify(queryService).query(argThat(c -> c.clientAppId() == 10L
                && "预算如何审批".equals(c.query())
                && c.finalLimit() == 8));
    }

    /** 业务拒答（无相关知识库）同样返回 200，答案为空 */
    @Test
    void returns200ForBusinessRefusal() {
        RagQueryResponse domain = new RagQueryResponse(RagQueryDecision.NO_RELEVANT_KNOWLEDGE_BASE,
                null, List.of(), List.of(), null);
        when(queryService.query(any())).thenReturn(domain);

        ResponseEntity<ApiResponse<RagQueryResponseView>> result = controller.query(
                new RagQueryRequest("无关问题", 8), request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data().decision()).isEqualTo("NO_RELEVANT_KNOWLEDGE_BASE");
        assertThat(result.getBody().data().answer()).isNull();
    }

    /** 答案生成不可用（未配置 ChatModel）映射为 503 */
    @Test
    void returns503WhenGenerationUnavailable() {
        RagQueryResponse domain = new RagQueryResponse(RagQueryDecision.GENERATION_UNAVAILABLE,
                null, List.of(), List.of(), "trace-1");
        when(queryService.query(any())).thenReturn(domain);

        ResponseEntity<ApiResponse<RagQueryResponseView>> result = controller.query(
                new RagQueryRequest("预算如何审批", 8), request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result.getBody().code()).isEqualTo("GENERATION_UNAVAILABLE");
        assertThat(result.getBody().data()).isNull();
    }

    /** query 为空：抛 400 业务异常，不调用服务 */
    @Test
    void rejectsBlankQuery() {
        assertThatThrownBy(() -> controller.query(new RagQueryRequest("  ", 8), request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(((BusinessException) e).code()).isEqualTo("VALIDATION_ERROR");
                });
        verify(queryService, org.mockito.Mockito.never()).query(any());
    }

    /** finalLimit 小于 1：抛 400 业务异常 */
    @Test
    void rejectsInvalidFinalLimit() {
        assertThatThrownBy(() -> controller.query(new RagQueryRequest("预算如何审批", 0), request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    /** finalLimit 缺省：使用默认值 8 调用服务 */
    @Test
    void defaultsFinalLimitWhenAbsent() {
        RagQueryResponse domain = new RagQueryResponse(RagQueryDecision.INSUFFICIENT_EVIDENCE,
                null, List.of(), List.of(), null);
        when(queryService.query(any())).thenReturn(domain);

        controller.query(new RagQueryRequest("预算如何审批", null), request());

        verify(queryService).query(argThat(c -> c.finalLimit() == 8));
    }

    /** 缺少调用方身份：抛出异常（由全局异常处理兜底），不调用服务 */
    @Test
    void failsFastWhenNoCallerPrincipal() {
        MockHttpServletRequest noPrincipal = new MockHttpServletRequest("POST", "/api/v1/rag/query");

        assertThatThrownBy(() -> controller.query(new RagQueryRequest("预算如何审批", 8), noPrincipal))
                .isInstanceOf(IllegalStateException.class);
        verify(queryService, org.mockito.Mockito.never()).query(any());
    }

    /** 构造携带调用方主体的请求 */
    private HttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/rag/query");
        request.setAttribute("rag.callerPrincipal",
                new CallerPrincipal(10L, "app-a", "应用A"));
        return request;
    }
}
