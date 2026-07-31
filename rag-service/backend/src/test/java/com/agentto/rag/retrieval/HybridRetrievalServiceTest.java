package com.agentto.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.agentto.rag.embedding.EmbeddingService;
import com.agentto.rag.index.ChunkIndex;
import com.agentto.rag.index.IndexSearchHit;
import com.agentto.rag.index.IndexedChunk;
import com.agentto.rag.index.SearchScope;

class HybridRetrievalServiceTest {

    @Test
    void keepsEveryStageScoreAndUsesRerankAsFinalOrder() {
        RecordingTraceRecorder recorder = new RecordingTraceRecorder();
        HybridRetrievalService service = new HybridRetrievalService(
                new FixedEmbedding(), new FixedIndex(), new FixedRerank(), recorder, new RrfFusion(60));

        RetrievalResponse response = service.search(new RetrievalRequest("预算如何审查", 20, 20, 20, 10, 3));

        assertThat(response.fallbackReason()).isNull();
        assertThat(response.candidates()).extracting(RetrievalCandidate::chunkId)
                .containsExactly("chunk-b", "chunk-c", "chunk-a");
        RetrievalCandidate first = response.candidates().get(0);
        assertThat(first.keywordRank()).isEqualTo(2);
        assertThat(first.vectorRank()).isEqualTo(1);
        assertThat(first.rrfRank()).isEqualTo(1);
        assertThat(first.rerankRank()).isEqualTo(1);
        assertThat(first.finalRank()).isEqualTo(1);
        assertThat(recorder.candidates).hasSize(3);
        assertThat(response.traceUid()).isEqualTo("trace-test");
    }

    @Test
    void fallsBackToRrfWhenRerankFails() {
        RecordingTraceRecorder recorder = new RecordingTraceRecorder();
        RerankService failingRerank = new RerankService() {
            @Override public List<RerankScore> rerank(String query, List<String> texts) {
                throw new IllegalStateException("service unavailable");
            }
            @Override public boolean healthy() { return false; }
        };
        HybridRetrievalService service = new HybridRetrievalService(
                new FixedEmbedding(), new FixedIndex(), failingRerank, recorder, new RrfFusion(60));

        RetrievalResponse response = service.search(new RetrievalRequest("预算如何审查", 20, 20, 20, 10, 3));

        assertThat(response.fallbackReason()).startsWith("RERANK_UNAVAILABLE");
        assertThat(response.candidates()).extracting(RetrievalCandidate::chunkId)
                .containsExactly("chunk-b", "chunk-a", "chunk-c");
        assertThat(response.candidates()).extracting(RetrievalCandidate::finalRank).containsExactly(1, 2, 3);
    }

    @Test
    void recordsAllFusedCandidatesEvenWhenFinalResponseIsShorter() {
        RecordingTraceRecorder recorder = new RecordingTraceRecorder();
        HybridRetrievalService service = new HybridRetrievalService(
                new FixedEmbedding(), new FixedIndex(), new FixedRerank(), recorder, new RrfFusion(60));

        RetrievalResponse response = service.search(new RetrievalRequest("预算如何审查", 20, 20, 20, 10, 2));

        assertThat(response.candidates()).hasSize(2);
        assertThat(recorder.candidates).hasSize(3);
        assertThat(recorder.candidates).filteredOn(candidate -> candidate.finalRank() != null).hasSize(2);
    }

    @Test
    void removesDuplicateContentBeforeRerankButKeepsItInTheTrace() {
        RecordingTraceRecorder recorder = new RecordingTraceRecorder();
        HybridRetrievalService service = new HybridRetrievalService(
                new FixedEmbedding(), new DuplicateContentIndex(), new OrderPreservingRerank(), recorder,
                new RrfFusion(60));

        RetrievalResponse response = service.search(new RetrievalRequest("HITL 是什么", 20, 20, 20, 10, 8));

        assertThat(response.candidates()).hasSize(3);
        assertThat(response.candidates()).extracting(RetrievalCandidate::contentHash).doesNotHaveDuplicates();
        assertThat(recorder.candidates).hasSize(4);
        assertThat(recorder.candidates).filteredOn(
                candidate -> candidate.dedupeStatus() == DedupeStatus.DUPLICATE)
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.duplicateOfChunkId()).isEqualTo("chunk-a"));
    }

    @Test
    void reportsEveryRealRetrievalStageWithCounts() {
        RecordingProgress progress = new RecordingProgress();
        RecordingTraceRecorder recorder = new RecordingTraceRecorder();
        HybridRetrievalService service = new HybridRetrievalService(
                new FixedEmbedding(), new FixedIndex(), new FixedRerank(),
                recorder, new RrfFusion(60));

        service.search(new RetrievalRequest("预算如何审查", 20, 20, 20, 2, 2), progress);

        assertThat(progress.events).containsExactly(
                "RUNNING:PREPROCESS", "COMPLETED:PREPROCESS:1",
                "RUNNING:KEYWORD", "COMPLETED:KEYWORD:2",
                "RUNNING:EMBEDDING", "COMPLETED:EMBEDDING:null",
                "RUNNING:VECTOR", "COMPLETED:VECTOR:2",
                "RUNNING:FUSION", "COMPLETED:FUSION:3",
                "RUNNING:DEDUPE", "COMPLETED:DEDUPE:3",
                "RUNNING:RERANK", "COMPLETED:RERANK:2",
                "RUNNING:COMPLETE", "COMPLETED:COMPLETE:2");
        assertThat(recorder.context.report().events())
                .extracting(event -> event.stage())
                .containsExactly("PREPROCESS", "KEYWORD", "EMBEDDING", "VECTOR", "FUSION", "DEDUPE", "RERANK");
    }

    @Test
    void reportsEmbeddingAndVectorFallbackWithoutFailingTheSearch() {
        EmbeddingService failingEmbedding = new EmbeddingService() {
            @Override public List<float[]> embed(List<String> texts) {
                throw new IllegalStateException("embedding offline");
            }
            @Override public boolean healthy() { return false; }
        };
        RecordingProgress progress = new RecordingProgress();
        HybridRetrievalService service = new HybridRetrievalService(
                failingEmbedding, new FixedIndex(), new FixedRerank(),
                new RecordingTraceRecorder(), new RrfFusion(60));

        RetrievalResponse response = service.search(
                new RetrievalRequest("预算如何审查", 20, 20, 20, 2, 2), progress);

        assertThat(response.fallbackReason()).startsWith("EMBEDDING_UNAVAILABLE");
        assertThat(progress.events).contains("DEGRADED:EMBEDDING", "SKIPPED:VECTOR");
        assertThat(progress.events).doesNotContain("RUNNING:VECTOR");
    }

    @Test
    void reportsRerankFallbackWithoutFailingTheSearch() {
        RerankService failingRerank = new RerankService() {
            @Override public List<RerankScore> rerank(String query, List<String> texts) {
                throw new IllegalStateException("rerank offline");
            }
            @Override public boolean healthy() { return false; }
        };
        RecordingProgress progress = new RecordingProgress();
        HybridRetrievalService service = new HybridRetrievalService(
                new FixedEmbedding(), new FixedIndex(), failingRerank,
                new RecordingTraceRecorder(), new RrfFusion(60));

        RetrievalResponse response = service.search(
                new RetrievalRequest("预算如何审查", 20, 20, 20, 2, 2), progress);

        assertThat(response.fallbackReason()).startsWith("RERANK_UNAVAILABLE");
        assertThat(progress.events).contains("DEGRADED:RERANK", "COMPLETED:COMPLETE:2");
    }

    @Test
    void reportsVectorFailureAndStopsTheSearch() {
        RecordingProgress progress = new RecordingProgress();
        HybridRetrievalService service = new HybridRetrievalService(
                new FixedEmbedding(), new VectorFailingIndex(), new FixedRerank(),
                new RecordingTraceRecorder(), new RrfFusion(60));

        assertThatThrownBy(() -> service.search(
                new RetrievalRequest("预算如何审查", 20, 20, 20, 2, 2), progress))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vector offline");

        assertThat(progress.events).contains("FAILED:VECTOR");
        assertThat(progress.events).doesNotContain("DEGRADED:EMBEDDING", "SKIPPED:VECTOR");
    }

    private static final class FixedEmbedding implements EmbeddingService {
        @Override public List<float[]> embed(List<String> texts) { return List.of(new float[] { 1, 0, 0 }); }
        @Override public boolean healthy() { return true; }
    }

    private static final class FixedIndex implements ChunkIndex {
        @Override public void ensureIndex() { }
        @Override public void replaceVersionChunks(Long versionId, List<IndexedChunk> chunks) { }
        @Override public List<IndexSearchHit> keywordSearch(String query, SearchScope scope, int limit) {
            return List.of();
        }
        @Override public List<IndexSearchHit> vectorSearch(float[] queryVector, SearchScope scope, int limit) {
            return List.of();
        }
        @Override @Deprecated public List<IndexSearchHit> keywordSearch(String query, int limit) {
            return List.of(hit("chunk-a", "A", 8.2), hit("chunk-b", "B", 7.1));
        }
        @Override @Deprecated public List<IndexSearchHit> vectorSearch(float[] queryVector, int limit) {
            return List.of(hit("chunk-b", "B", 0.91), hit("chunk-c", "C", 0.86));
        }
        @Override public boolean healthy() { return true; }
        @Override public String indexVersion() { return "test"; }
        private IndexSearchHit hit(String id, String content, double score) {
            return new IndexSearchHit(id, content, "制度", 1L, 2L, 1L, 0, score, Map.of("page", "1"));
        }
    }

    private static final class FixedRerank implements RerankService {
        @Override public List<RerankScore> rerank(String query, List<String> texts) {
            return List.of(new RerankScore(0, 0.95, 1), new RerankScore(2, 0.80, 2),
                    new RerankScore(1, 0.20, 3));
        }
        @Override public boolean healthy() { return true; }
    }

    private static final class OrderPreservingRerank implements RerankService {
        @Override public List<RerankScore> rerank(String query, List<String> texts) {
            List<RerankScore> scores = new ArrayList<>();
            for (int index = 0; index < texts.size(); index++) {
                scores.add(new RerankScore(index, 1.0 - index * 0.1, index + 1));
            }
            return List.copyOf(scores);
        }
        @Override public boolean healthy() { return true; }
    }

    private static final class DuplicateContentIndex implements ChunkIndex {
        @Override public void ensureIndex() { }
        @Override public void replaceVersionChunks(Long versionId, List<IndexedChunk> chunks) { }
        @Override public List<IndexSearchHit> keywordSearch(String query, SearchScope scope, int limit) {
            return List.of();
        }
        @Override public List<IndexSearchHit> vectorSearch(float[] queryVector, SearchScope scope, int limit) {
            return List.of();
        }
        @Override @Deprecated public List<IndexSearchHit> keywordSearch(String query, int limit) {
            return List.of(hit("chunk-a", "HITL 是什么", 8.2), hit("chunk-c", "RACI 是什么", 7.1));
        }
        @Override @Deprecated public List<IndexSearchHit> vectorSearch(float[] queryVector, int limit) {
            return List.of(hit("chunk-b", "ＨＩＴＬ\n 是什么", 0.91), hit("chunk-d", "审批流程", 0.86));
        }
        @Override public boolean healthy() { return true; }
        @Override public String indexVersion() { return "test"; }
        private IndexSearchHit hit(String id, String content, double score) {
            return new IndexSearchHit(id, content, "制度", 1L, 2L, 1L, 0, score, Map.of("page", "1"));
        }
    }

    private static final class VectorFailingIndex implements ChunkIndex {
        private final FixedIndex delegate = new FixedIndex();

        @Override public void ensureIndex() { }
        @Override public void replaceVersionChunks(Long versionId, List<IndexedChunk> chunks) { }
        @Override public List<IndexSearchHit> keywordSearch(String query, SearchScope scope, int limit) {
            return List.of();
        }
        @Override public List<IndexSearchHit> vectorSearch(float[] queryVector, SearchScope scope, int limit) {
            throw new IllegalStateException("vector offline");
        }
        @Override @Deprecated public List<IndexSearchHit> keywordSearch(String query, int limit) {
            return delegate.keywordSearch(query, limit);
        }
        @Override @Deprecated public List<IndexSearchHit> vectorSearch(float[] queryVector, int limit) {
            throw new IllegalStateException("vector offline");
        }
        @Override public boolean healthy() { return false; }
        @Override public String indexVersion() { return "test"; }
    }

    private static final class RecordingTraceRecorder implements TraceRecorder {
        private List<RetrievalCandidate> candidates = new ArrayList<>();
        private RetrievalTraceContext context;
        @Override public String record(RetrievalRequest request, List<RetrievalCandidate> values,
                RetrievalTimings timings, String fallbackReason) {
            candidates = List.copyOf(values);
            return "trace-test";
        }

        @Override public String record(RetrievalRequest request, List<RetrievalCandidate> values,
                RetrievalTimings timings, String fallbackReason, RetrievalTraceContext value) {
            candidates = List.copyOf(values);
            context = value;
            return "trace-test";
        }
    }

    private static final class RecordingProgress implements RetrievalProgressReporter {
        private final List<String> events = new ArrayList<>();

        @Override public void running(RetrievalStage stage) {
            events.add("RUNNING:" + stage);
        }

        @Override public void completed(RetrievalStage stage, long elapsedMs, Integer itemCount) {
            events.add("COMPLETED:" + stage + ":" + itemCount);
        }

        @Override public void degraded(RetrievalStage stage, long elapsedMs, String message) {
            events.add("DEGRADED:" + stage);
        }

        @Override public void skipped(RetrievalStage stage, String message) {
            events.add("SKIPPED:" + stage);
        }

        @Override public void failed(RetrievalStage stage, String message) {
            events.add("FAILED:" + stage);
        }
    }
}
