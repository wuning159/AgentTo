package com.agentto.rag.quality;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖率策略测试：验证 pom.xml 已声明 JaCoCo 插件和核心包清单。
 * 核心包（routing、evidence、citation、query）将在后续 Task 中逐步建立，
 * 本测试提前锁定覆盖率门禁配置结构，确保新包一加入就有覆盖率约束。
 */
class CoveragePolicyTest {

    @Test
    void corePackagesAreDeclaredForCoverageEnforcement() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom)
                .contains("jacoco-maven-plugin")
                .contains("com/agentto/rag/routing")
                .contains("com/agentto/rag/evidence")
                .contains("com/agentto/rag/citation")
                .contains("com/agentto/rag/query");
    }
}
