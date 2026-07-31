package com.agentto.rag.embedding;

import java.util.List;

public interface EmbeddingService {
    List<float[]> embed(List<String> texts);
    boolean healthy();
}
