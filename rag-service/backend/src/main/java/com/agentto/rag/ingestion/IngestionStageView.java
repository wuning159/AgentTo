package com.agentto.rag.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.agentto.rag.observability.TechnicalStageDetail;
import com.fasterxml.jackson.databind.ObjectMapper;

public record IngestionStageView(
        String stage,
        String status,
        String detail,
        Integer itemCount,
        Instant startedAt,
        Instant finishedAt,
        Long elapsedMs,
        TechnicalStageDetail technicalDetail) {
    static IngestionStageView from(IngestionStage value, ObjectMapper objectMapper) {
        return new IngestionStageView(value.getStageCode(), value.getStatus(), value.getDetailMessage(),
                value.getItemCount(), value.getStartedAt(), value.getFinishedAt(), value.getElapsedMs(),
                detail(value.getTechnicalDetailJson(), objectMapper));
    }

    private static TechnicalStageDetail detail(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, TechnicalStageDetail.class);
        } catch (Exception ignored) {
            return new TechnicalStageDetail("技术详情无法解析", null, null, Map.of(),
                    Map.of("storedDetailInvalid", true), List.of(), Map.of());
        }
    }
}
