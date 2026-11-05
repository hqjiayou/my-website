package com.mywebsite.agent.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 检索请求的空间线格式（Issue #209 UoW-3）：{@code spaces[{baseId,
 * embeddingClasses[]}]} 取代 UoW-2 的 {@code baseIds}＋请求级
 * {@code embeddingClasses}——登记迁空间后每空间带自己的类集。无类空间不带
 * {@code embeddingClasses} 字段、{@code spaces} 为 null 时整字段缺席
 * （NON_NULL 省略，与旧版无空间报文同形）。
 */
class AgentRetrievalSearchRequestWireTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void spacesCarryPerSpaceClassesAndReplaceBaseIds() throws Exception {
        AgentRetrievalSearchRequest request = new AgentRetrievalSearchRequest(
                "7",
                "query",
                null,
                null,
                null,
                null,
                List.of(
                        new AgentRetrievalSearchSpace(
                                "901",
                                List.of(new AgentEmbeddingClass("openai:embed-a", "key-a", null))),
                        new AgentRetrievalSearchSpace("902", null)));

        String json = mapper.writeValueAsString(request);

        assertThat(json)
                .contains(
                        "\"spaces\":[{\"baseId\":\"901\",\"embeddingClasses\":[{\"embeddingSpec\":\"openai:embed-a\",\"apiKey\":\"key-a\"}]},{\"baseId\":\"902\"}]")
                .doesNotContain("\"baseIds\"");
    }

    @Test
    void requestWithoutSpacesOmitsSpacesField() throws Exception {
        String json = mapper.writeValueAsString(new AgentRetrievalSearchRequest("7", "query", null));
        assertThat(json).doesNotContain("spaces").doesNotContain("baseIds");
    }
}
