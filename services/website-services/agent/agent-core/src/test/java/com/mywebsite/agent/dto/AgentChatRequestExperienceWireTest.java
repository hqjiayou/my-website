package com.mywebsite.agent.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * experienceLessons 的线格式（Issue #184 UoW-4）：可选字段向后兼容——带条目时
 * 序列化出 lessonNo＋lesson（title 不入报文），不带时整字段缺席、报文与旧版一致。
 */
class AgentChatRequestExperienceWireTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requestWithoutLessonsOmitsExperienceField() throws Exception {
        String json = mapper.writeValueAsString(new AgentChatRequest("10", "hello"));
        assertThat(json).doesNotContain("experienceLessons");
    }

    @Test
    void requestWithLessonsWritesNumberAndTextOnly() throws Exception {
        AgentChatRequest request = new AgentChatRequest(
                "10", "hello", null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                List.of(new AgentExperienceLessonItem(6, "先枚举再逐份抽取")), null);
        String json = mapper.writeValueAsString(request);
        assertThat(json).contains("\"experienceLessons\":[{\"lessonNo\":6,\"lesson\":\"先枚举再逐份抽取\"}]");
    }
}
