package com.agentto.rag.asset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ContentAssetReferenceProbe {

    private final JdbcTemplate jdbcTemplate;

    public ContentAssetReferenceProbe(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isReferenced(Long assetId, String sha256) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from rag_document_version where content_asset_id = ? or sha256 = ?",
                Integer.class, assetId, sha256);
        return count != null && count > 0;
    }
}
