package com.mywebsite.agent.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mywebsite.agent")
public class AgentProperties {

    /** agent-services 根地址，如 http://127.0.0.1:3001 */
    private String baseUrl = "http://127.0.0.1:3001";

    private long connectTimeoutMs = 2000L;

    private long readTimeoutMs = 20000L;

    /** 与 agent-services AGENT_INTERNAL_SERVICE_TOKEN 共享 */
    private String internalServiceToken = "";

    /**
     * 连接失败 / 502–504 的额外重试次数（不含首次）。chat 对 4xx / blocked 不重试。
     */
    private int retryMaxAttempts = 1;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(long readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public String getInternalServiceToken() {
        return internalServiceToken;
    }

    public void setInternalServiceToken(String internalServiceToken) {
        this.internalServiceToken = internalServiceToken;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }
}
