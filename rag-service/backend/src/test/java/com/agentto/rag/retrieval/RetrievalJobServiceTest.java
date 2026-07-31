package com.agentto.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

import com.agentto.rag.common.api.BusinessException;

class RetrievalJobServiceTest {

    private final RetrievalRequest request = new RetrievalRequest("HITL是什么", 20, 20, 30, 15, 8, 1L);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-16T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsQueuedJobAndPublishesCompletedSnapshotAfterExecution() {
        HybridRetrievalService retrieval = mock(HybridRetrievalService.class);
        RetrievalResponse response = response();
        when(retrieval.search(eq(request), any())).thenAnswer(invocation -> {
            RetrievalProgressReporter reporter = invocation.getArgument(1);
            reporter.running(RetrievalStage.KEYWORD);
            reporter.completed(RetrievalStage.KEYWORD, 86, 20);
            reporter.running(RetrievalStage.COMPLETE);
            reporter.completed(RetrievalStage.COMPLETE, 2820, 8);
            return response;
        });
        ControlledExecutor executor = new ControlledExecutor();
        RetrievalJobService service = new RetrievalJobService(retrieval, executor, clock);

        String jobUid = service.create(request);

        assertThat(jobUid).isNotBlank();
        assertThat(service.get(jobUid).status()).isEqualTo(RetrievalJobStatus.QUEUED);
        executor.runNext();

        RetrievalJobSnapshot snapshot = service.get(jobUid);
        assertThat(snapshot.status()).isEqualTo(RetrievalJobStatus.COMPLETED);
        assertThat(snapshot.result()).isEqualTo(response);
        assertThat(snapshot.currentStage()).isEqualTo(RetrievalStage.COMPLETE);
        assertThat(snapshot.stages()).filteredOn(stage -> stage.stage() == RetrievalStage.KEYWORD)
                .singleElement()
                .satisfies(stage -> {
                    assertThat(stage.status()).isEqualTo(RetrievalStageStatus.COMPLETED);
                    assertThat(stage.elapsedMs()).isEqualTo(86);
                    assertThat(stage.itemCount()).isEqualTo(20);
                });
    }

    @Test
    void keepsFailedStageAndErrorWhenRetrievalCannotContinue() {
        HybridRetrievalService retrieval = mock(HybridRetrievalService.class);
        when(retrieval.search(eq(request), any())).thenAnswer(invocation -> {
            RetrievalProgressReporter reporter = invocation.getArgument(1);
            reporter.running(RetrievalStage.VECTOR);
            reporter.failed(RetrievalStage.VECTOR, "vector offline");
            throw new IllegalStateException("vector offline");
        });
        ControlledExecutor executor = new ControlledExecutor();
        RetrievalJobService service = new RetrievalJobService(retrieval, executor, clock);
        String jobUid = service.create(request);

        executor.runNext();

        RetrievalJobSnapshot snapshot = service.get(jobUid);
        assertThat(snapshot.status()).isEqualTo(RetrievalJobStatus.FAILED);
        assertThat(snapshot.currentStage()).isEqualTo(RetrievalStage.VECTOR);
        assertThat(snapshot.error()).isEqualTo("vector offline");
        assertThat(snapshot.result()).isNull();
    }

    @Test
    void rejectsUnknownJobUid() {
        RetrievalJobService service = new RetrievalJobService(
                mock(HybridRetrievalService.class), new ControlledExecutor(), clock);

        assertThatThrownBy(() -> service.get("missing"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code())
                        .isEqualTo("RETRIEVAL_JOB_NOT_FOUND"));
    }

    private RetrievalResponse response() {
        return new RetrievalResponse("trace-test", List.of(), null,
                new RetrievalTimings(100, 20, 30, 2, 2000, 2152));
    }

    private static final class ControlledExecutor implements Executor {
        private final Queue<Runnable> commands = new ArrayDeque<>();

        @Override public void execute(Runnable command) {
            commands.add(command);
        }

        void runNext() {
            Runnable command = commands.remove();
            command.run();
        }
    }
}
