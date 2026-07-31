package com.agentto.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.agentto.rag.auth.AdminSessionRepository;
import com.agentto.rag.auth.AdminUser;
import com.agentto.rag.auth.AdminUserRepository;
import com.agentto.rag.observability.ExecutionEvent;
import com.agentto.rag.observability.ExecutionReport;
import com.agentto.rag.observability.TechnicalStageDetail;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:trace_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class QueryTraceServiceTest {

    @Autowired private QueryTraceService traceService;
    @Autowired private QueryTraceRepository traceRepository;
    @Autowired private QueryCandidateRepository candidateRepository;
    @Autowired private AdminUserRepository userRepository;
    @Autowired private AdminSessionRepository sessionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long adminId;

    @BeforeEach
    void setUp() {
        candidateRepository.deleteAll();
        traceRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        adminId = userRepository.save(AdminUser.create("trace-admin", "Trace 管理员",
                passwordEncoder.encode("password"))).getId();
    }

    @Test
    void recordsAndReadsEveryRetrievalStage() {
        RetrievalRequest request = new RetrievalRequest("预算如何审查", 20, 20, 20, 10, 5, adminId);
        RetrievalCandidate candidate = RetrievalCandidate.keyword("chunk-a", "预算必须经过财务审查", 8.2, 1)
                .withRrf(0.032, 1)
                .withDedupe(ContentFingerprint.sha256("预算必须经过财务审查"), DedupeStatus.KEPT, null)
                .withRerank(0.96, 1).withFinalRank(1);
        RetrievalTimings timings = new RetrievalTimings(11, 12, 13, 14, 15, 70);
        Instant started = Instant.parse("2026-07-16T01:00:00Z");
        ExecutionReport report = new ExecutionReport(true, List.of(new ExecutionEvent(
                "FUSION", "COMPLETED", started.toString(), started.plusMillis(14).toString(), 14L,
                new TechnicalStageDetail("使用排名倒数融合两路候选", 40, 20,
                        Map.of("rankConstant", 60), Map.of(), List.of(), Map.of()))));

        String traceUid = traceService.record(request, List.of(candidate), timings, null,
                new RetrievalTraceContext(60, 1, report));

        QueryTraceDetail detail = traceService.detail(traceUid);
        assertThat(detail.query()).isEqualTo("预算如何审查");
        assertThat(detail.resultCount()).isEqualTo(1);
        assertThat(detail.timings().totalMs()).isEqualTo(70);
        assertThat(detail.rankConstant()).isEqualTo(60);
        assertThat(detail.deduplicatedCount()).isEqualTo(1);
        assertThat(detail.executionReport()).isEqualTo(report);
        assertThat(detail.limits().fusionLimit()).isEqualTo(20);
        assertThat(detail.candidates()).singleElement().satisfies(value -> {
            assertThat(value.chunkId()).isEqualTo("chunk-a");
            assertThat(value.content()).isEqualTo("预算必须经过财务审查");
            assertThat(value.keywordScore()).isEqualTo(8.2);
            assertThat(value.rrfRank()).isEqualTo(1);
            assertThat(value.rerankScore()).isEqualTo(0.96);
            assertThat(value.finalRank()).isEqualTo(1);
            assertThat(value.contentHash()).isEqualTo(candidate.contentHash());
            assertThat(value.dedupeStatus()).isEqualTo("KEPT");
            assertThat(value.duplicateOfChunkId()).isNull();
        });
        assertThat(traceService.recent(10)).extracting(QueryTraceSummary::traceUid).contains(traceUid);
    }
}
