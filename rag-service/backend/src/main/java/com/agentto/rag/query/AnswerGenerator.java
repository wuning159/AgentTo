package com.agentto.rag.query;

import java.util.List;

import com.agentto.rag.citation.GeneratedAnswer;
import com.agentto.rag.retrieval.RetrievalCandidate;

/**
 * 答案生成器：基于检索证据生成带引用的答案。
 *
 * 实现约定：
 * - 只接收已通过 EvidenceGate 的候选
 * - 证据不支持时返回空答案（text 为空白），不得伪造回答
 * - 每个主要结论必须引用证据切片 chunkId，quote 逐字来自切片
 */
public interface AnswerGenerator {

    /**
     * 基于证据生成答案。
     *
     * @param query    原始查询
     * @param evidence 已通过证据门的检索候选列表
     * @return 生成答案，证据不足时 text 为空白
     */
    GeneratedAnswer generate(String query, List<RetrievalCandidate> evidence);
}
