package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Java 从模型入口选出的本次调用凭证。只出现在 Java→Python 的内部请求里，不回给浏览器。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentModelSelection(String spec, String apiKey, String baseUrl, String embeddingSpec) {

    public AgentModelSelection(String spec, String apiKey, String baseUrl) {
        this(spec, apiKey, baseUrl, null);
    }
}
