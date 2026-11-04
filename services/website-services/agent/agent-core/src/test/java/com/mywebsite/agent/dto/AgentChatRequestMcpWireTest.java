package com.mywebsite.agent.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * mcpServerNames 的线格式（Issue #192 UoW-3）：三态——INHERIT（null）整字段
 * 缺席、报文与旧版一致；OFF（空列表）写出 {@code "mcpServerNames":[]}（与缺席
 * 严格区分，执行层据此不注册任何 MCP）；CUSTOM 写出名字数组。
 */
class AgentChatRequestMcpWireTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private AgentChatRequest requestWithNames(List<String> names) {
        return new AgentChatRequest(
                "10", "hello", null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, names);
    }

    @Test
    void inheritOmitsMcpField() throws Exception {
        String json = mapper.writeValueAsString(requestWithNames(null));
        assertThat(json).doesNotContain("mcpServerNames");
    }

    @Test
    void offWritesEmptyArray() throws Exception {
        String json = mapper.writeValueAsString(requestWithNames(List.of()));
        assertThat(json).contains("\"mcpServerNames\":[]");
    }

    @Test
    void customWritesNames() throws Exception {
        String json = mapper.writeValueAsString(requestWithNames(List.of("docs", "echo")));
        assertThat(json).contains("\"mcpServerNames\":[\"docs\",\"echo\"]");
    }
}
