package com.mywebsite.agent.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.agent.dto.AgentExperienceLessonItem;
import com.mywebsite.agent.entity.AgentExperienceLessonEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 已批准经验条目的下发形状转换（Issue #184 UoW-4）。 */
class AgentExperienceLessonUtilsTest {

    private static AgentExperienceLessonEntity lesson(Integer lessonNo, String text) {
        AgentExperienceLessonEntity entity = new AgentExperienceLessonEntity();
        entity.setLessonNo(lessonNo);
        entity.setLesson(text);
        entity.setStatus(AgentExperienceLessonEntity.STATUS_APPROVED);
        return entity;
    }

    @Test
    void wireKeepsOrderAndMapsNumberAndText() {
        List<AgentExperienceLessonItem> wired =
                AgentExperienceLessonUtils.wire(List.of(lesson(6, "先枚举再逐份抽取"), lesson(7, "工资默认实发口径")));

        assertThat(wired)
                .containsExactly(
                        new AgentExperienceLessonItem(6, "先枚举再逐份抽取"),
                        new AgentExperienceLessonItem(7, "工资默认实发口径"));
    }

    @Test
    void wireSkipsEntriesWithoutNumberOrText() {
        List<AgentExperienceLessonItem> wired = AgentExperienceLessonUtils.wire(
                List.of(lesson(null, "无编号"), lesson(8, "  "), lesson(9, "有效条目")));

        assertThat(wired).containsExactly(new AgentExperienceLessonItem(9, "有效条目"));
    }

    @Test
    void wireOfEmptyOrNullIsEmptyList() {
        assertThat(AgentExperienceLessonUtils.wire(null)).isEmpty();
        assertThat(AgentExperienceLessonUtils.wire(List.of())).isEmpty();
    }
}
