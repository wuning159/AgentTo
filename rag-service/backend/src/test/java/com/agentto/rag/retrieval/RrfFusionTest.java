package com.agentto.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class RrfFusionTest {

    @Test
    void fusesKeywordAndVectorRanksWithoutLosingStageScores() {
        RetrievalCandidate keywordFirst = RetrievalCandidate.keyword("chunk-a", "A", 8.2, 1);
        RetrievalCandidate keywordSecond = RetrievalCandidate.keyword("chunk-b", "B", 7.1, 2);
        RetrievalCandidate vectorFirst = RetrievalCandidate.vector("chunk-b", "B", 0.91, 1);
        RetrievalCandidate vectorSecond = RetrievalCandidate.vector("chunk-c", "C", 0.86, 2);

        List<RetrievalCandidate> result = new RrfFusion(60).fuse(
                List.of(keywordFirst, keywordSecond),
                List.of(vectorFirst, vectorSecond),
                10);

        assertThat(result).extracting(RetrievalCandidate::chunkId)
                .containsExactly("chunk-b", "chunk-a", "chunk-c");
        assertThat(result.getFirst().keywordRank()).isEqualTo(2);
        assertThat(result.getFirst().vectorRank()).isEqualTo(1);
        assertThat(result.getFirst().rrfRank()).isEqualTo(1);
        assertThat(result.getFirst().rrfScore()).isGreaterThan(result.get(1).rrfScore());
    }

    @Test
    void usesChunkIdAsStableTieBreaker() {
        List<RetrievalCandidate> result = new RrfFusion(60).fuse(
                List.of(RetrievalCandidate.keyword("b", "B", 1, 1)),
                List.of(RetrievalCandidate.vector("a", "A", 1, 1)),
                10);

        assertThat(result).extracting(RetrievalCandidate::chunkId).containsExactly("a", "b");
    }
}
