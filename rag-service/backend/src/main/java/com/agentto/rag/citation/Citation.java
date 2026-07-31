package com.agentto.rag.citation;

/**
 * 答案引用：指向证据切片中的一段逐字原文。
 *
 * @param chunkId 证据切片 ID，必须属于本次检索的证据集合
 * @param quote   引用原文，必须逐字来自对应切片内容
 */
public record Citation(String chunkId, String quote) {
}
