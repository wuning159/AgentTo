package com.agentto.rag.document;

import java.util.List;

public record DocumentDetail(DocumentSummary document, List<DocumentVersionView> versions) {
}
