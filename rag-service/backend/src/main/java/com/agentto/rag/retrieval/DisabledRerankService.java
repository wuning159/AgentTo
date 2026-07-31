package com.agentto.rag.retrieval;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "rag.tei", name = "rerank-enabled", havingValue = "false")
public class DisabledRerankService implements RerankService {

    @Override
    public List<RerankScore> rerank(String query, List<String> texts) {
        throw new IllegalStateException("Rerank 服务未启用");
    }

    @Override
    public boolean healthy() {
        return false;
    }
}
