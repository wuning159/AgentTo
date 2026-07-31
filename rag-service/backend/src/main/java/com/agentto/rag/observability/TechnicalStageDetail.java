package com.agentto.rag.observability;

import java.util.List;
import java.util.Map;

public record TechnicalStageDetail(
        String summary,
        Integer inputCount,
        Integer outputCount,
        Map<String, Object> parameters,
        Map<String, Object> metrics,
        List<Map<String, Object>> samples,
        Map<String, Object> raw) {

    public TechnicalStageDetail {
        summary = summary == null ? "" : summary;
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        samples = samples == null ? List.of() : List.copyOf(samples);
        raw = raw == null ? Map.of() : Map.copyOf(raw);
    }
}
