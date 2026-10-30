package com.mywebsite.admin.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** proxy-services 进程地址与管理口令。口令只在服务端使用，不进接口响应。 */
@ConfigurationProperties(prefix = "mywebsite.proxy-services")
public class AdminProxyServicesProperties {

    private String baseUrl = "http://127.0.0.1:8317";

    private String managementKey = "";

    private int connectTimeoutMs = 2000;

    private int readTimeoutMs = 10000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getManagementKey() {
        return managementKey;
    }

    public void setManagementKey(String managementKey) {
        this.managementKey = managementKey;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
