package com.mywebsite.infra.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mywebsite.ai")
public class ModelEntrySecretProperties {

    /** 模型入口 api_key 的加密口令。空则按明文读写（仅兼容旧数据）。 */
    private String modelEntrySecret = "";

    public String getModelEntrySecret() {
        return modelEntrySecret;
    }

    public void setModelEntrySecret(String modelEntrySecret) {
        this.modelEntrySecret = modelEntrySecret;
    }
}
