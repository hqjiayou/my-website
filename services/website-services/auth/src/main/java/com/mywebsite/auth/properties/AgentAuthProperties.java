package com.mywebsite.auth.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Agent 登录策略配置（前缀 {@code security.agent-auth}）。 */
@ConfigurationProperties(prefix = "security.agent-auth")
public class AgentAuthProperties {

    private int agentLoginMaxPasswordLength = 128;

    /** 认证限流总开关（逃生阀）；关闭后登录 / 刷新均不做限流检查与计数。 */
    private boolean agentRateLimitEnabled = true;

    private int agentLoginIpAgentUserFailureThreshold = 5;

    private int agentLoginIpAgentUserWindowSeconds = 300;

    private int agentLoginAgentUserFailureThreshold = 10;

    private int agentLoginAgentUserWindowSeconds = 900;

    private int agentLoginAgentIpFailureThreshold = 20;

    private int agentLoginAgentIpWindowSeconds = 900;

    private int agentRefreshIpFailureThreshold = 30;

    private int agentRefreshIpWindowSeconds = 600;

    public int getAgentLoginMaxPasswordLength() {
        return agentLoginMaxPasswordLength;
    }

    public void setAgentLoginMaxPasswordLength(int agentLoginMaxPasswordLength) {
        this.agentLoginMaxPasswordLength = agentLoginMaxPasswordLength;
    }

    public boolean isAgentRateLimitEnabled() {
        return agentRateLimitEnabled;
    }

    public void setAgentRateLimitEnabled(boolean agentRateLimitEnabled) {
        this.agentRateLimitEnabled = agentRateLimitEnabled;
    }

    public int getAgentLoginIpAgentUserFailureThreshold() {
        return agentLoginIpAgentUserFailureThreshold;
    }

    public void setAgentLoginIpAgentUserFailureThreshold(int agentLoginIpAgentUserFailureThreshold) {
        this.agentLoginIpAgentUserFailureThreshold = agentLoginIpAgentUserFailureThreshold;
    }

    public int getAgentLoginIpAgentUserWindowSeconds() {
        return agentLoginIpAgentUserWindowSeconds;
    }

    public void setAgentLoginIpAgentUserWindowSeconds(int agentLoginIpAgentUserWindowSeconds) {
        this.agentLoginIpAgentUserWindowSeconds = agentLoginIpAgentUserWindowSeconds;
    }

    public int getAgentLoginAgentUserFailureThreshold() {
        return agentLoginAgentUserFailureThreshold;
    }

    public void setAgentLoginAgentUserFailureThreshold(int agentLoginAgentUserFailureThreshold) {
        this.agentLoginAgentUserFailureThreshold = agentLoginAgentUserFailureThreshold;
    }

    public int getAgentLoginAgentUserWindowSeconds() {
        return agentLoginAgentUserWindowSeconds;
    }

    public void setAgentLoginAgentUserWindowSeconds(int agentLoginAgentUserWindowSeconds) {
        this.agentLoginAgentUserWindowSeconds = agentLoginAgentUserWindowSeconds;
    }

    public int getAgentLoginAgentIpFailureThreshold() {
        return agentLoginAgentIpFailureThreshold;
    }

    public void setAgentLoginAgentIpFailureThreshold(int agentLoginAgentIpFailureThreshold) {
        this.agentLoginAgentIpFailureThreshold = agentLoginAgentIpFailureThreshold;
    }

    public int getAgentLoginAgentIpWindowSeconds() {
        return agentLoginAgentIpWindowSeconds;
    }

    public void setAgentLoginAgentIpWindowSeconds(int agentLoginAgentIpWindowSeconds) {
        this.agentLoginAgentIpWindowSeconds = agentLoginAgentIpWindowSeconds;
    }

    public int getAgentRefreshIpFailureThreshold() {
        return agentRefreshIpFailureThreshold;
    }

    public void setAgentRefreshIpFailureThreshold(int agentRefreshIpFailureThreshold) {
        this.agentRefreshIpFailureThreshold = agentRefreshIpFailureThreshold;
    }

    public int getAgentRefreshIpWindowSeconds() {
        return agentRefreshIpWindowSeconds;
    }

    public void setAgentRefreshIpWindowSeconds(int agentRefreshIpWindowSeconds) {
        this.agentRefreshIpWindowSeconds = agentRefreshIpWindowSeconds;
    }
}
