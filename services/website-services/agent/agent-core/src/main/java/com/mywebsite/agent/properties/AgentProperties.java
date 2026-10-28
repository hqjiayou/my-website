package com.mywebsite.agent.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mywebsite.agent")
public class AgentProperties {

    /** agent-services 根地址，如 http://127.0.0.1:3001 */
    private String baseUrl = "http://127.0.0.1:3001";

    private long connectTimeoutMs = 2000L;

    /** 须盖住单次工具超时（默认 30s）以及站点 SSE 的 120s。同步 chat 用它做整段等待。 */
    private long readTimeoutMs = 120_000L;

    /** 与 agent-services AGENT_INTERNAL_SERVICE_TOKEN 共享 */
    private String internalServiceToken = "";

    /**
     * 连接失败 / 502–504 的额外重试次数（不含首次）。chat 对 4xx / blocked 不重试。
     */
    private int retryMaxAttempts = 1;

    /**
     * Python 回调本机 agent-app internal 的根地址。空则不在请求里覆盖，沿用 Python 的 BUSINESS_API_BASE_URL。
     */
    private String businessApiBaseUrl = "";

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

    public String getBusinessApiBaseUrl() {
        return businessApiBaseUrl;
    }

    public void setBusinessApiBaseUrl(String businessApiBaseUrl) {
        this.businessApiBaseUrl = businessApiBaseUrl;
    }
}
