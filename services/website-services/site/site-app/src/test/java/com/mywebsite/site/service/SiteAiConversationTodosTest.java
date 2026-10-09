package com.mywebsite.site.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.dto.AgentTodoItem;
import com.mywebsite.site.entity.SiteAiMessageEntity;
import com.mywebsite.site.mapper.SiteAiConversationMapper;
import com.mywebsite.site.mapper.SiteAiMessageMapper;
import com.mywebsite.site.service.SiteAiConversationService.ChatPreparation;
import com.mywebsite.site.testsupport.AbstractSiteSpringBootTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** §21.2：会话级 Todo 快照往返（写入 → 下一轮读出 → 清空写回 NULL）；§22.8：运行元数据随 ASSISTANT 消息落库。 */
class SiteAiConversationTodosTest extends AbstractSiteSpringBootTest {

    @Autowired
    private SiteAiConversationService conversationService;

    @Autowired
    private SiteAiConversationMapper conversationMapper;

    @Autowired
    private SiteAiMessageMapper messageMapper;

    @Test
    void todosRoundTripAcrossTurnsAndClearToNull() {
        ChatPreparation first = conversationService.prepare(10L, 200L, null);
        assertThat(first.todos()).isEmpty();

        List<AgentTodoItem> snapshot = List.of(
                new AgentTodoItem("t1", "Step 1", "COMPLETED"), new AgentTodoItem("t2", "Step 2", "IN_PROGRESS"));
        conversationService.recordExchange(first.conversation(), "plan it", "ok", snapshot, null);
        String conversationId = String.valueOf(first.conversation().getId());

        ChatPreparation second = conversationService.prepare(10L, 200L, conversationId);
        assertThat(second.todos()).containsExactlyElementsOf(snapshot);

        conversationService.recordExchange(second.conversation(), "next", "done", List.of(), null);
        assertThat(conversationMapper.selectById(second.conversation().getId()).getAgentTodos()).isNull();
        assertThat(conversationService.prepare(10L, 200L, conversationId).todos()).isEmpty();
    }

    @Test
    void malformedSnapshotIsIgnored() {
        ChatPreparation first = conversationService.prepare(10L, 201L, null);
        conversationService.recordExchange(first.conversation(), "hi", "ok", List.of(), null);
        var entity = conversationMapper.selectById(first.conversation().getId());
        entity.setAgentTodos("{not-json");
        conversationMapper.updateById(entity);

        ChatPreparation next = conversationService.prepare(10L, 201L, String.valueOf(entity.getId()));
        assertThat(next.todos()).isEmpty();
    }

    @Test
    void runMetaIsStoredOnAssistantMessageOnly() {
        ChatPreparation first = conversationService.prepare(10L, 202L, null);
        AgentRunMeta meta = new AgentRunMeta(
                new AgentRunMeta.Usage(2, 1, 30, 5),
                List.of(new AgentRunMeta.ToolCall("list_todos", true)),
                List.of("orchestration"));
        conversationService.recordExchange(first.conversation(), "hi", "ok", List.of(), meta);

        List<SiteAiMessageEntity> messages = messageMapper.selectList(new LambdaQueryWrapper<SiteAiMessageEntity>()
                .eq(SiteAiMessageEntity::getConversationId, first.conversation().getId())
                .orderByAsc(SiteAiMessageEntity::getId));
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getAgentMeta()).isNull();
        assertThat(messages.get(1).getAgentMeta())
                .contains("\"toolCalls\":1")
                .contains("\"name\":\"list_todos\"")
                .contains("orchestration");
    }
}
