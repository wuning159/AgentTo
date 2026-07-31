package com.agentto.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ContentDeduplicatorTest {

    @Test
    void mergesContentThatOnlyDiffersByUnicodeWidthAndWhitespace() {
        RetrievalCandidate first = RetrievalCandidate.keyword("chunk-a", "HITL 是什么", 1, 1)
                .withRrf(0.030, 1);
        RetrievalCandidate duplicate = RetrievalCandidate.keyword("chunk-b", "ＨＩＴＬ\n  是什么", 1, 2)
                .withRrf(0.029, 2);
        RetrievalCandidate different = RetrievalCandidate.keyword("chunk-c", "RACI 是什么", 1, 3)
                .withRrf(0.028, 3);

        DedupeResult result = new ContentDeduplicator().dedupe(List.of(first, duplicate, different), 8);

        assertThat(result.selected()).extracting(RetrievalCandidate::chunkId)
                .containsExactly("chunk-a", "chunk-c");
        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(result.traceCandidates()).filteredOn(candidate -> candidate.chunkId().equals("chunk-b"))
                .first()
                .satisfies(candidate -> {
                    assertThat(candidate.dedupeStatus()).isEqualTo(DedupeStatus.DUPLICATE);
                    assertThat(candidate.duplicateOfChunkId()).isEqualTo("chunk-a");
                    assertThat(candidate.contentHash()).isEqualTo(firstContentHash(result));
                });
    }

    @Test
    void appliesLimitAfterRemovingDuplicateContent() {
        List<RetrievalCandidate> ranked = List.of(
                candidate("a", "same", 0.040, 1),
                candidate("b", "same", 0.039, 2),
                candidate("c", "different one", 0.038, 3),
                candidate("d", "different two", 0.037, 4));

        DedupeResult result = new ContentDeduplicator().dedupe(ranked, 3);

        assertThat(result.selected()).extracting(RetrievalCandidate::chunkId)
                .containsExactly("a", "c", "d");
    }

    @Test
    void keepsTextWithDifferentPunctuationAsDifferentContent() {
        DedupeResult result = new ContentDeduplicator().dedupe(List.of(
                candidate("a", "制度已经通过。", 0.040, 1),
                candidate("b", "制度已经通过？", 0.039, 2)), 8);

        assertThat(result.selected()).hasSize(2);
        assertThat(result.duplicateCount()).isZero();
    }

    private RetrievalCandidate candidate(String id, String content, double score, int rank) {
        return RetrievalCandidate.keyword(id, content, 1, rank).withRrf(score, rank);
    }

    private String firstContentHash(DedupeResult result) {
        return result.traceCandidates().stream()
                .filter(candidate -> candidate.chunkId().equals("chunk-a"))
                .findFirst().orElseThrow().contentHash();
    }
}
