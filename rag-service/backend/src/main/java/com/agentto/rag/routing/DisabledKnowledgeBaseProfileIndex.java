package com.agentto.rag.routing;

import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 禁用的知识库画像索引实现，当 Elasticsearch 未启用时使用。
 * 所有检索返回空列表，健康检查返回 false。
 */
@Service
@ConditionalOnProperty(prefix = "rag.elasticsearch", name = "enabled", havingValue = "false")
public class DisabledKnowledgeBaseProfileIndex implements KnowledgeBaseProfileIndex {

    @Override
    public List<KnowledgeBaseProfileCandidate> search(float[] queryVector,
            Set<Long> accessibleKnowledgeBaseIds, int limit) {
        return List.of();
    }

    @Override
    public String indexVersion() {
        return "disabled";
    }

    @Override
    public boolean healthy() {
        return false;
    }
}
