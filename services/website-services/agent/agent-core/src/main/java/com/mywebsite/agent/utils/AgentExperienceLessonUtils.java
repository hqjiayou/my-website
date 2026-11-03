package com.mywebsite.agent.utils;

import com.mywebsite.agent.dto.AgentExperienceLessonItem;
import com.mywebsite.agent.entity.AgentExperienceLessonEntity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * 已批准经验条目的下发形状转换（Issue #184 UoW-4）。admin 试聊与 agent-app
 * 正式对话共用这一套，与 {@link AgentEmbeddingClassUtils} 的跨模块先例一致。
 */
public final class AgentExperienceLessonUtils {

    private AgentExperienceLessonUtils() {}

    /**
     * 实体列表（{@code AgentExperienceLessonService.listApproved()} 已按 L 编号
     * 升序）→ 下发条目，顺序不变。编号缺失或 lesson 空白的条目不下发：已批准
     * 条目本不应出现这种数据，当作脏数据跳过，不让一条坏数据污染整段组装。
     * 空结果回空列表，调用方据此传 null（报文与未启用时逐字一致）。
     */
    public static List<AgentExperienceLessonItem> wire(List<AgentExperienceLessonEntity> approved) {
        if (approved == null || approved.isEmpty()) {
            return List.of();
        }
        List<AgentExperienceLessonItem> wired = new ArrayList<>(approved.size());
        for (AgentExperienceLessonEntity entity : approved) {
            if (entity == null || entity.getLessonNo() == null || !StringUtils.hasText(entity.getLesson())) {
                continue;
            }
            wired.add(new AgentExperienceLessonItem(entity.getLessonNo(), entity.getLesson()));
        }
        return List.copyOf(wired);
    }
}
