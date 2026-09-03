package com.agentto.rag.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ContentAssetLifecyclePropertiesTest {

    @Test
    void fillsPositiveDefaults() {
        ContentAssetLifecycleProperties properties = new ContentAssetLifecycleProperties(null, null, null, null, null,
                null);
        assertThat(properties.unreferencedTtl()).isEqualTo(Duration.ofHours(168));
        assertThat(properties.pendingLease()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void rejectsZeroOrNegativeTtl() {
        assertThatThrownBy(() -> new ContentAssetLifecycleProperties(Duration.ZERO, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unreferenced-ttl");
        assertThatThrownBy(() -> new ContentAssetLifecycleProperties(Duration.ofHours(-1), null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unreferenced-ttl");
    }
}
