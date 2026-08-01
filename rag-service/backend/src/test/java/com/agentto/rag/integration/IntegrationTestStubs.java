package com.agentto.rag.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.agentto.rag.citation.Citation;
import com.agentto.rag.citation.GeneratedAnswer;
import com.agentto.rag.embedding.EmbeddingService;
import com.agentto.rag.query.AnswerGenerator;
import com.agentto.rag.retrieval.RerankScore;
import com.agentto.rag.retrieval.RerankService;
import com.agentto.rag.retrieval.RetrievalCandidate;
import com.agentto.rag.rewrite.QueryRewriter;

/**
 * 集成测试确定性 stub 组件。
 *
 * <p>替换外部模型依赖，使测试可重复且不依赖网络：
 * <ul>
 *   <li>{@link EmbeddingService}：字符 bigram 词袋向量（L2 归一化），相似文本得到相似向量，
 *       让真实 Elasticsearch KNN 检索在测试中产生有意义的结果；</li>
 *   <li>{@link RerankService}：保持输入顺序，分数随名次递减；</li>
 *   <li>{@link AnswerGenerator}：引用首条证据生成固定答案，quote 逐字来自切片，可通过引用校验；</li>
 *   <li>{@link QueryRewriter}：不产生改写，避免触发真实模型调用。</li>
 * </ul>
 */
@TestConfiguration
public class IntegrationTestStubs {

    /** 与 application-integration.yml 中 rag.elasticsearch.dimensions 保持一致 */
    public static final int DIMENSIONS = 128;

    @Bean
    @Primary
    EmbeddingService deterministicEmbeddingService() {
        return new EmbeddingService() {
            @Override
            public List<float[]> embed(List<String> texts) {
                return texts.stream().map(IntegrationTestStubs::embed).toList();
            }

            @Override
            public boolean healthy() {
                return true;
            }
        };
    }

    @Bean
    @Primary
    RerankService identityRerankService() {
        return new RerankService() {
            @Override
            public List<RerankScore> rerank(String query, List<String> texts) {
                List<RerankScore> scores = new ArrayList<>();
                for (int index = 0; index < texts.size(); index++) {
                    scores.add(new RerankScore(index, 1.0 / (index + 1), index + 1));
                }
                return scores;
            }

            @Override
            public boolean healthy() {
                return true;
            }
        };
    }

    @Bean
    @Primary
    AnswerGenerator stubAnswerGenerator() {
        return (query, evidence) -> {
            if (evidence.isEmpty()) {
                return new GeneratedAnswer("", List.of());
            }
            RetrievalCandidate top = evidence.get(0);
            String content = top.content() == null ? "" : top.content();
            String quote = content.length() > 40 ? content.substring(0, 40) : content;
            return new GeneratedAnswer("根据检索资料，" + quote, List.of(new Citation(top.chunkId(), quote)));
        };
    }

    @Bean
    @Primary
    QueryRewriter noRewriteQueryRewriter() {
        return (originalQuery, failureReason) -> Optional.empty();
    }

    /**
     * 确定性向量：字符 bigram 词袋计数后 L2 归一化。
     *
     * @param text 输入文本
     * @return 归一化向量
     */
    public static float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text != null) {
            String normalized = text.toLowerCase().replaceAll("\\s+", "");
            for (int index = 0; index + 1 < normalized.length(); index++) {
                int hash = (normalized.charAt(index) * 31 + normalized.charAt(index + 1)) & 0x7fffffff;
                vector[hash % DIMENSIONS] += 1.0f;
            }
            if (normalized.length() == 1) {
                vector[normalized.charAt(0) % DIMENSIONS] += 1.0f;
            }
        }
        double norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int index = 0; index < vector.length; index++) {
                vector[index] = (float) (vector[index] / norm);
            }
        }
        return vector;
    }
}
