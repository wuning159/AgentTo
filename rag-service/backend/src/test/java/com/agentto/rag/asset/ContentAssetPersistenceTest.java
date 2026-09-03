package com.agentto.rag.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:content_asset_persistence;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class ContentAssetPersistenceTest {

    @Autowired
    private ContentAssetRepository repository;

    @Test
    void findsBySha256WithoutScanningAllRows() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        ContentAsset saved = repository.save(ContentAsset.stored("c".repeat(64), "b", "k", 4, "text/plain", now));

        assertThat(repository.findBySha256("c".repeat(64))).hasValueSatisfying(found -> {
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getStorageState()).isEqualTo(ContentAssetState.READY);
        });
        assertThat(repository.findBySha256("d".repeat(64))).isEmpty();
    }
}
