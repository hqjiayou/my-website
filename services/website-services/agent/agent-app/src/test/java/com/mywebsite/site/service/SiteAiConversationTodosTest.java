package com.mywebsite.site.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.agent.dto.AgentAttachmentDto;
import com.mywebsite.agent.dto.AgentChatHistoryMessage;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.dto.AgentTodoItem;
import com.mywebsite.site.dto.SiteAiAttachmentDto;
import com.mywebsite.site.dto.SiteAiMessageDto;
import com.mywebsite.site.entity.SiteAiMessageEntity;
import com.mywebsite.site.mapper.SiteAiConversationMapper;
import com.mywebsite.site.mapper.SiteAiMessageMapper;
import com.mywebsite.site.service.SiteAiConversationService.ChatPreparation;
import com.mywebsite.site.testsupport.AbstractSiteSpringBootTest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    @Test
    void userAttachmentsAreStoredAsJsonAndAssistantStaysEmpty() {
        ChatPreparation first = conversationService.prepare(10L, 210L, null);
        AgentAttachmentDto image = image("persist");
        conversationService.recordExchange(first.conversation(), "see", "ok", List.of(), null, List.of(image));

        List<SiteAiMessageEntity> messages = messageMapper.selectList(new LambdaQueryWrapper<SiteAiMessageEntity>()
                .eq(SiteAiMessageEntity::getConversationId, first.conversation().getId())
                .orderByAsc(SiteAiMessageEntity::getId));
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getAttachments()).contains("mediaType", "image/png", image.data());
        assertThat(messages.get(1).getAttachments()).isNull();

        List<SiteAiMessageDto> listed =
                conversationService.listMessages(10L, 210L, first.conversation().getId());
        assertThat(listed.get(0).attachments())
                .containsExactly(new SiteAiAttachmentDto(image.mediaType(), image.data()));
        assertThat(listed.get(1).attachments()).isNull();

        ChatPreparation second =
                conversationService.prepare(10L, 210L, String.valueOf(first.conversation().getId()));
        assertThat(second.history().get(0).attachments()).containsExactly(image);
    }

    @Test
    void historyReplayKeepsFourNewestImagesAndReplacesDroppedEmptyText() {
        ChatPreparation first = conversationService.prepare(10L, 211L, null);
        AgentAttachmentDto oldest = image("old");
        conversationService.recordExchange(first.conversation(), "", "a", List.of(), null, List.of(oldest));
        String conversationId = String.valueOf(first.conversation().getId());

        conversationService.recordExchange(
                conversationService.prepare(10L, 211L, conversationId).conversation(),
                "mid",
                "b",
                List.of(),
                null,
                List.of(image("b1"), image("b2"), image("b3")));
        conversationService.recordExchange(
                conversationService.prepare(10L, 211L, conversationId).conversation(),
                "new",
                "c",
                List.of(),
                null,
                List.of(image("c")));

        List<SiteAiMessageEntity> stored = messageMapper.selectList(new LambdaQueryWrapper<SiteAiMessageEntity>()
                .eq(SiteAiMessageEntity::getConversationId, first.conversation().getId())
                .eq(SiteAiMessageEntity::getRole, SiteAiMessageEntity.ROLE_USER)
                .orderByAsc(SiteAiMessageEntity::getId));
        assertThat(stored.get(0).getContent()).isEmpty();

        List<AgentChatHistoryMessage> users = conversationService
                .prepare(10L, 211L, conversationId)
                .history()
                .stream()
                .filter(message -> "USER".equals(message.role()))
                .toList();
        assertThat(users).hasSize(3);
        assertThat(users.get(0).content()).isEqualTo("[图片]");
        assertThat(users.get(0).attachments()).isNull();
        assertThat(users.get(1).content()).isEqualTo("mid");
        assertThat(users.get(1).attachments()).hasSize(3);
        assertThat(users.get(2).content()).isEqualTo("new");
        assertThat(users.get(2).attachments()).containsExactly(image("c"));
    }

    @Test
    void historyReplayKeepsPartialImagesWhenTextIsEmpty() {
        ChatPreparation first = conversationService.prepare(10L, 212L, null);
        AgentAttachmentDto firstImage = image("keep");
        AgentAttachmentDto dropped = image("drop");
        conversationService.recordExchange(
                first.conversation(), "", "a", List.of(), null, List.of(firstImage, dropped));
        String conversationId = String.valueOf(first.conversation().getId());
        conversationService.recordExchange(
                conversationService.prepare(10L, 212L, conversationId).conversation(),
                "later",
                "b",
                List.of(),
                null,
                List.of(image("n1"), image("n2"), image("n3")));

        List<AgentChatHistoryMessage> users = conversationService
                .prepare(10L, 212L, conversationId)
                .history()
                .stream()
                .filter(message -> "USER".equals(message.role()))
                .toList();
        assertThat(users.get(0).content()).isEmpty();
        assertThat(users.get(0).attachments()).containsExactly(firstImage);
        assertThat(users.get(1).attachments()).hasSize(3);
    }

    @Test
    void absentCitationsKeepExistingAgentMeta() {
        AgentRunMeta meta = new AgentRunMeta(
                new AgentRunMeta.Usage(2, 1, 30, 5),
                List.of(new AgentRunMeta.ToolCall("list_todos", true)),
                List.of("orchestration"));
        ChatPreparation withMeta = conversationService.prepare(10L, 214L, null);
        conversationService.recordExchange(
                withMeta.conversation(), "hi", "ok", List.of(), meta, null, List.of());

        List<SiteAiMessageEntity> stored = messagesOf(withMeta.conversation().getId());
        assertThat(stored.get(0).getAgentMeta()).isNull();
        assertThat(stored.get(1).getAgentMeta())
                .contains("\"toolCalls\":1")
                .contains("\"name\":\"list_todos\"")
                .contains("orchestration")
                .doesNotContain("citations");
        assertThat(conversationService.listMessages(10L, 214L, withMeta.conversation().getId()).get(1).citations())
                .isNull();

        ChatPreparation empty = conversationService.prepare(10L, 215L, null);
        conversationService.recordExchange(empty.conversation(), "hi", "ok", List.of(), null, null, null);
        assertThat(messagesOf(empty.conversation().getId()).get(1).getAgentMeta()).isNull();
        assertThat(conversationService.listMessages(10L, 215L, empty.conversation().getId()).get(1).citations())
                .isNull();
    }

    @Test
    void malformedAgentMetaDoesNotFailMessageList() {
        ChatPreparation first = conversationService.prepare(10L, 213L, null);
        conversationService.recordExchange(first.conversation(), "hi", "ok", List.of(), null);
        SiteAiMessageEntity assistant = messagesOf(first.conversation().getId()).get(1);
        assistant.setAgentMeta("{not-json");
        messageMapper.updateById(assistant);

        List<SiteAiMessageDto> broken = conversationService.listMessages(10L, 213L, first.conversation().getId());
        assertThat(broken).hasSize(2);
        assertThat(broken.get(1).content()).isEqualTo("ok");
        assertThat(broken.get(1).citations()).isNull();

        assistant.setAgentMeta("{\"citations\":{\"sourceId\":\"kb:1\"}}");
        messageMapper.updateById(assistant);
        assertThat(conversationService.listMessages(10L, 213L, first.conversation().getId()).get(1).citations())
                .isNull();
    }

    private List<SiteAiMessageEntity> messagesOf(long conversationId) {
        return messageMapper.selectList(new LambdaQueryWrapper<SiteAiMessageEntity>()
                .eq(SiteAiMessageEntity::getConversationId, conversationId)
                .orderByAsc(SiteAiMessageEntity::getId));
    }

    private static AgentAttachmentDto image(String seed) {
        return new AgentAttachmentDto(
                "image/png", Base64.getEncoder().encodeToString(seed.getBytes(StandardCharsets.UTF_8)));
    }
}
