package com.agentto.rag.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import com.agentto.rag.common.api.BusinessException;
import com.agentto.rag.evaluation.RagFailureCode;
import com.agentto.rag.evidence.EvidenceDecision;
import com.agentto.rag.observability.ExecutionEvent;
import com.agentto.rag.observability.TechnicalStageDetail;
import com.agentto.rag.routing.RoutingDecision;

/**
 * 公共查询编排 Trace 服务测试。
 * 覆盖记录后详情完整可读（含 JSON 列往返）、列表查询和不存在的详情报错。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:flow_trace_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class QueryFlowTraceServiceTest {

    @Autowired private QueryFlowTraceService traceService;
    @Autowired private QueryFlowTraceRepository traceRepository;

    @BeforeEach
    void setUp() {
        traceRepository.deleteAll();
    }

    /** 记录后详情完整可读：全部字段含 JSON 列往返一致 */
    @Test
    void recordsAndReadsFullFlowTrace() {
        QueryFlowTrace snapshot = new QueryFlowTrace(10L, "预算如何审批", "预算审批流程", 8,
                RagQueryDecision.ANSWERED, RoutingDecision.ROUTED,
                List.of(101L, 102L, 103L),
                List.of(new QueryFlowTrace.KnowledgeBaseSelection(101L, 0.82),
                        new QueryFlowTrace.KnowledgeBaseSelection(102L, 0.71)),
                EvidenceDecision.SUFFICIENT, true, true, null, 2,
                List.of("trace-1", "trace-2"),
                List.of(new ExecutionEvent(QueryFlowStage.ROUTE_PROFILE.name(), "COMPLETED",
                        "2026-08-01T10:00:00Z", "2026-08-01T10:00:00.100Z", 100L,
                        new TechnicalStageDetail("画像召回 Top 3", null, 3, null,
                                Map.of(), null, null))),
                15, 320L);

        traceService.record(snapshot);

        List<QueryFlowTraceSummary> summaries = traceService.recent(10);
        assertThat(summaries).hasSize(1);
        QueryFlowTraceSummary summary = summaries.get(0);
        assertThat(summary.flowTraceUid()).isNotBlank();
        assertThat(summary.clientAppId()).isEqualTo(10L);
        assertThat(summary.originalQuery()).isEqualTo("预算如何审批");
        assertThat(summary.decision()).isEqualTo("ANSWERED");
        assertThat(summary.attemptCount()).isEqualTo(2);
        assertThat(summary.totalMs()).isEqualTo(320L);

        QueryFlowTraceDetail detail = traceService.detail(summary.flowTraceUid());
        assertThat(detail.clientAppId()).isEqualTo(10L);
        assertThat(detail.originalQuery()).isEqualTo("预算如何审批");
        assertThat(detail.effectiveQuery()).isEqualTo("预算审批流程");
        assertThat(detail.decision()).isEqualTo("ANSWERED");
        assertThat(detail.routingDecision()).isEqualTo("ROUTED");
        assertThat(detail.profileShortlist()).containsExactly(101L, 102L, 103L);
        assertThat(detail.selectedKnowledgeBases()).hasSize(2);
        assertThat(detail.selectedKnowledgeBases().get(0).score()).isEqualTo(0.82);
        assertThat(detail.evidenceDecision()).isEqualTo("SUFFICIENT");
        assertThat(detail.rewriteAttempted()).isTrue();
        assertThat(detail.citationValid()).isTrue();
        assertThat(detail.failureCode()).isNull();
        assertThat(detail.traceUids()).containsExactly("trace-1", "trace-2");
        assertThat(detail.events()).hasSize(1);
        assertThat(detail.events().get(0).stage()).isEqualTo("ROUTE_PROFILE");
        assertThat(detail.events().get(0).detail().outputCount()).isEqualTo(3);
        assertThat(detail.answerLength()).isEqualTo(15);
        assertThat(detail.totalMs()).isEqualTo(320L);
    }

    /** 拒答场景：可空字段保持 null，故障码可诊断 */
    @Test
    void preservesNullableFieldsAndFailureCode() {
        QueryFlowTrace snapshot = new QueryFlowTrace(11L, "无关问题", "无关问题", 8,
                RagQueryDecision.GENERATION_UNAVAILABLE, RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE,
                List.of(), List.of(), null, false, null,
                RagFailureCode.MODEL_FAILURE, 0, List.of(), List.of(), 0, 50L);

        traceService.record(snapshot);

        QueryFlowTraceDetail detail = traceService.detail(
                traceService.recent(10).get(0).flowTraceUid());
        assertThat(detail.evidenceDecision()).isNull();
        assertThat(detail.citationValid()).isNull();
        assertThat(detail.failureCode()).isEqualTo("MODEL_FAILURE");
        assertThat(detail.profileShortlist()).isEmpty();
        assertThat(detail.selectedKnowledgeBases()).isEmpty();
        assertThat(detail.traceUids()).isEmpty();
        assertThat(detail.events()).isEmpty();
    }

    /** 不存在的编排 Trace：返回 404 业务异常 */
    @Test
    void throwsNotFoundForUnknownTrace() {
        assertThatThrownBy(() -> traceService.detail("no-such-trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).status())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
