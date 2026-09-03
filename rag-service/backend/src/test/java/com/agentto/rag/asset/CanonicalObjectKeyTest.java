package com.agentto.rag.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CanonicalObjectKeyTest {

    @Test
    void buildsPrefixedContentAddressedKey() {
        String sha = "e76bda917de8995693adb36b33262160e895318a635ca11ac8272b3a630c37b1";
        assertThat(CanonicalObjectKey.of(sha))
                .isEqualTo("content-assets/sha256/e7/" + sha);
    }

    @Test
    void rejectsInvalidSha() {
        assertThatThrownBy(() -> CanonicalObjectKey.of("not-a-hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64");
    }
}
