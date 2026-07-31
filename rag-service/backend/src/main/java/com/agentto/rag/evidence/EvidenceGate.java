package com.agentto.rag.evidence;

import java.util.List;

import org.springframework.stereotype.Component;

import com.agentto.rag.retrieval.RetrievalCandidate;
import com.agentto.rag.routing.KnowledgeBaseRoute;
import com.agentto.rag.routing.RoutingDecision;

/**
 * 证据门：纯函数判定检索证据是否足以生成答案。
 *
 * 判定逻辑：
 * 1. 如果路由已拒绝（NO_RELEVANT_KNOWLEDGE_BASE），直接返回同一决策
 * 2. 候选列表为空 → INSUFFICIENT_EVIDENCE
 * 3. 最高分数低于阈值 → INSUFFICIENT_EVIDENCE
 * 4. 合格证据数量不足 → INSUFFICIENT_EVIDENCE
 * 5. 全部通过 → SUFFICIENT
 *
 * 分数优先使用 rerankScore，没有 rerank 时使用 rrfScore，并在 reason 中标记降级。
 */
@Component
public class EvidenceGate {

    private final EvidencePolicyProperties properties;

    public EvidenceGate(EvidencePolicyProperties properties) {
        this.properties = properties;
    }

    /**
     * 评估证据是否充足。
     *
     * @param route      路由结果
     * @param candidates 检索候选列表（已去重、已排序）
     * @return 证据评估结果
     */
    public EvidenceAssessment assess(KnowledgeBaseRoute route, List<RetrievalCandidate> candidates) {
        // 路由已拒绝
        if (route.decision() == RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE) {
            return new EvidenceAssessment(EvidenceDecision.NO_RELEVANT_KNOWLEDGE_BASE,
                    0.0, 0, "路由阶段无相关知识库");
        }

        // 候选为空
        if (candidates == null || candidates.isEmpty()) {
            return new EvidenceAssessment(EvidenceDecision.INSUFFICIENT_EVIDENCE,
                    0.0, 0, "无候选证据");
        }

        // 判断分数来源：优先 rerank，降级到 rrf
        boolean useRerank = candidates.get(0).rerankScore() != null;
        String scoreSource = useRerank ? "rerank" : "rrf";

        // 计算最高分数和合格证据数
        double topScore = 0.0;
        int qualifyingCount = 0;
        double threshold = properties.getMinimumScore();

        for (RetrievalCandidate candidate : candidates) {
            double score = scoreOf(candidate);
            if (score > topScore) {
                topScore = score;
            }
            if (score >= threshold) {
                qualifyingCount++;
            }
        }

        // 最高分数低于阈值
        if (topScore < threshold) {
            return new EvidenceAssessment(EvidenceDecision.INSUFFICIENT_EVIDENCE,
                    topScore, qualifyingCount,
                    "最高分数 " + topScore + " 低于阈值 " + threshold + "（来源: " + scoreSource + "）");
        }

        // 合格证据数量不足
        if (qualifyingCount < properties.getMinimumCount()) {
            return new EvidenceAssessment(EvidenceDecision.INSUFFICIENT_EVIDENCE,
                    topScore, qualifyingCount,
                    "合格证据数 " + qualifyingCount + " 不足 " + properties.getMinimumCount()
                            + "（来源: " + scoreSource + "）");
        }

        // 证据充足
        return new EvidenceAssessment(EvidenceDecision.SUFFICIENT,
                topScore, qualifyingCount,
                "证据充足（来源: " + scoreSource + "）");
    }

    /**
     * 获取候选的分数。
     * 优先使用 rerankScore，其次 rrfScore，最后 0。
     */
    private double scoreOf(RetrievalCandidate candidate) {
        if (candidate.rerankScore() != null) {
            return candidate.rerankScore();
        }
        if (candidate.rrfScore() != null) {
            return candidate.rrfScore();
        }
        return 0.0;
    }
}
