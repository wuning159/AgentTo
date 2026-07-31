package com.agentto.rag.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 证据策略配置属性。
 * 控制证据门的最小分数阈值和最小合格证据数量。
 *
 * 阈值是初始基线，Task 11 用评测集校准，不在生产中静默自动修改。
 */
@ConfigurationProperties(prefix = "rag.evidence")
public class EvidencePolicyProperties {

    /** 候选证据的最低分数阈值，默认 0.55 */
    private double minimumScore = 0.55;

    /** 最少合格证据数量，默认 2 */
    private int minimumCount = 2;

    public double getMinimumScore() {
        return minimumScore;
    }

    public void setMinimumScore(double minimumScore) {
        this.minimumScore = minimumScore;
    }

    public int getMinimumCount() {
        return minimumCount;
    }

    public void setMinimumCount(int minimumCount) {
        this.minimumCount = minimumCount;
    }
}
