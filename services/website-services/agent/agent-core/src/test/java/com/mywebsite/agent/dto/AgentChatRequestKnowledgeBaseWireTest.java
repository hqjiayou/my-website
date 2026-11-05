package com.mywebsite.agent.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * knowledgeBaseIds 的线格式（Issue #209 UoW-2）：null 时整字段缺席、报文与旧版
 * 一致（执行层按无空间处理）；有值时写出字符串化空间 id 数组，执行层据此
 * 跨空间检索（向量分区键已改 base）。
 */
class AgentChatRequestKnowledgeBaseWireTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requestWithoutBasesOmitsKnowledgeBaseField() throws Exception {
        String json = mapper.writeValueAsString(new AgentChatRequest("10", "hello"));
        assertThat(json).doesNotContain("knowledgeBaseIds");
    }

    @Test
    void requestWithBasesWritesStringIds() throws Exception {
        AgentChatRequest request = new AgentChatRequest(
                "10", "hello", null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                List.of("9", "12"));
        String json = mapper.writeValueAsString(request);
        assertThat(json).contains("\"knowledgeBaseIds\":[\"9\",\"12\"]");
    }
}
