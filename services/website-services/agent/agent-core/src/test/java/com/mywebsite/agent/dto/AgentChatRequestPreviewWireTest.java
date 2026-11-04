package com.mywebsite.agent.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Issue #179：preview 标记的线格式——正式对话（null）不写出该字段、报文与旧版一致；
 * 试聊（TRUE）写出 {@code "preview":true}，供 Python ChatRequest.preview 门控卡片。
 */
class AgentChatRequestPreviewWireTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void formalRequestOmitsPreviewField() throws Exception {
        String json = mapper.writeValueAsString(new AgentChatRequest("10", "hello"));
        assertThat(json).doesNotContain("preview");
    }

    @Test
    void previewRequestWritesPreviewTrue() throws Exception {
        AgentChatRequest request = new AgentChatRequest(
                "10", "hello", null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, Boolean.TRUE, null, null);
        String json = mapper.writeValueAsString(request);
        assertThat(json).contains("\"preview\":true");
    }
}
