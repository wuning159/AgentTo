package com.agentto.rag.retrieval;

import java.util.List;

public interface RerankService {
    List<RerankScore> rerank(String query, List<String> texts);
    boolean healthy();
}
