package com.agentto.rag.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 路由配置属性。
 * 控制两阶段路由的召回上限、选中上限、每库验证条数和验证阈值。
 *
 * 阈值是初始基线，Task 11 用评测集校准，不在生产中静默自动修改。
 */
@ConfigurationProperties(prefix = "rag.routing")
public class RoutingProperties {

    /** 第一阶段画像召回上限，默认 10 */
    private int profileLimit = 10;

    /** 第二阶段最终选中的知识库上限，默认 3 */
    private int selectedLimit = 3;

    /** 第二阶段每个知识库的内容验证检索条数，默认 2 */
    private int verificationPerKbLimit = 2;

    /** 验证分数阈值，低于此分数的知识库不会被选中，默认 0.55 */
    private double verificationThreshold = 0.55;

    public int getProfileLimit() {
        return profileLimit;
    }

    public void setProfileLimit(int profileLimit) {
        this.profileLimit = profileLimit;
    }

    public int getSelectedLimit() {
        return selectedLimit;
    }

    public void setSelectedLimit(int selectedLimit) {
        this.selectedLimit = selectedLimit;
    }

    public int getVerificationPerKbLimit() {
        return verificationPerKbLimit;
    }

    public void setVerificationPerKbLimit(int verificationPerKbLimit) {
        this.verificationPerKbLimit = verificationPerKbLimit;
    }

    public double getVerificationThreshold() {
        return verificationThreshold;
    }

    public void setVerificationThreshold(double verificationThreshold) {
        this.verificationThreshold = verificationThreshold;
    }
}
