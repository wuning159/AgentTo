package com.agentto.rag.embedding;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "rag.tei", name = "embedding-enabled", havingValue = "false")
public class DisabledEmbeddingService implements EmbeddingService {

    @Override
    public List<float[]> embed(List<String> texts) {
        throw new IllegalStateException("Embedding 服务未启用");
    }

    @Override
    public boolean healthy() {
        return false;
    }
}
