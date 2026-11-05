package com.mywebsite.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.agent.dto.AgentAiConversationDto;
import com.mywebsite.agent.dto.AgentAiShareDto;
import com.mywebsite.agent.dto.AgentAiSharedConversationDto;
import com.mywebsite.agent.entity.AgentAiConversationEntity;
import com.mywebsite.agent.entity.AgentAiConversationShareEntity;
import com.mywebsite.agent.entity.AgentAiMessageEntity;
import com.mywebsite.agent.mapper.AgentAiConversationShareMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import com.mywebsite.agent.mapper.AgentAiConfigPresetMapper;
import com.mywebsite.agent.mapper.AgentAiConversationMapper;
import com.mywebsite.agent.mapper.AgentAiMessageMapper;
import com.mywebsite.agent.mapper.AgentMapper;
import com.mywebsite.agent.service.impl.AgentAiConversationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 会话重命名与硬删。Mapper 用 mock，才能断言非本人路径删除方法零调用。 */
@ExtendWith(MockitoExtension.class)
class AgentAiConversationManageTest {

    @Mock
    private AgentAiConversationMapper conversationMapper;

    @Mock
    private AgentAiMessageMapper messageMapper;

    @Mock
    private AgentMapper agentMapper;

    @Mock
    private AgentAiConfigPresetMapper configPresetMapper;

    @Mock
    private AgentAiConversationShareMapper shareMapper;

    private AgentAiConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentAiConversationServiceImpl(
                conversationMapper,
                messageMapper,
                agentMapper,
                configPresetMapper,
                shareMapper,
                new ObjectMapper());
    }

    @Test
    void renameOwnConversationTrimsTitle() {
        AgentAiConversationEntity entity = owned(1L, 10L, 200L, "old");
        when(conversationMapper.selectById(1L)).thenReturn(entity);

        AgentAiConversationDto dto = service.renameConversation(10L, 200L, 1L, "  new title  ");

        assertThat(dto.title()).isEqualTo("new title");
        assertThat(entity.getTitle()).isEqualTo("new title");
        verify(conversationMapper).updateById(entity);
    }

    @Test
    void renameOtherUsersConversationIsNotFound() {
        when(conversationMapper.selectById(1L)).thenReturn(owned(1L, 10L, 200L, "old"));

        assertThatThrownBy(() -> service.renameConversation(10L, 999L, 1L, "x"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
        verify(conversationMapper).selectById(1L);
        verifyNoMoreInteractions(conversationMapper);
    }

    @Test
    void deleteOwnConversationRemovesMessagesThenConversation() {
        when(conversationMapper.selectById(1L)).thenReturn(owned(1L, 10L, 200L, "t"));

        service.deleteConversation(10L, 200L, 1L);

        InOrder order = inOrder(conversationMapper, messageMapper);
        order.verify(conversationMapper).selectById(1L);
        order.verify(messageMapper).deleteByConversationId(1L);
        order.verify(conversationMapper).deleteByIdPhysical(1L);
    }

    @Test
    void deleteOtherUsersConversationIsNotFoundAndDoesNotDelete() {
        when(conversationMapper.selectById(1L)).thenReturn(owned(1L, 10L, 200L, "t"));

        assertThatThrownBy(() -> service.deleteConversation(10L, 999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
        verify(messageMapper, never()).deleteByConversationId(anyLong());
        verify(conversationMapper, never()).deleteByIdPhysical(anyLong());
        verifyNoInteractions(messageMapper);
        verify(conversationMapper).selectById(1L);
        verifyNoMoreInteractions(conversationMapper);
    }

    @Test
    void truncateOwnConversationDeletesFromMessageAndRefreshesMeta() {
        AgentAiConversationEntity conversation = owned(1L, 10L, 200L, "t");
        conversation.setCreatedAt(OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
        conversation.setAgentTodos("[{\"text\":\"x\"}]");
        AgentAiMessageEntity target = new AgentAiMessageEntity();
        target.setId(50L);
        target.setConversationId(1L);
        AgentAiMessageEntity remaining = new AgentAiMessageEntity();
        remaining.setId(40L);
        remaining.setConversationId(1L);
        remaining.setCreatedAt(OffsetDateTime.of(2026, 1, 2, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
        when(conversationMapper.selectById(1L)).thenReturn(conversation);
        when(messageMapper.selectById(50L)).thenReturn(target);
        when(messageMapper.deleteFromMessage(1L, 50L)).thenReturn(2);
        when(messageMapper.selectList(any())).thenReturn(List.of(remaining));

        service.truncateMessages(10L, 200L, 1L, 50L);

        verify(messageMapper).deleteFromMessage(1L, 50L);
        assertThat(conversation.getLastMessageAt()).isEqualTo(OffsetDateTime.of(2026, 1, 2, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
        assertThat(conversation.getAgentTodos()).isNull();
        verify(conversationMapper).updateById(conversation);
    }

    @Test
    void truncateAllMessagesFallsBackToConversationCreatedAt() {
        AgentAiConversationEntity conversation = owned(1L, 10L, 200L, "t");
        conversation.setCreatedAt(OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
        AgentAiMessageEntity target = new AgentAiMessageEntity();
        target.setId(10L);
        target.setConversationId(1L);
        when(conversationMapper.selectById(1L)).thenReturn(conversation);
        when(messageMapper.selectById(10L)).thenReturn(target);
        when(messageMapper.deleteFromMessage(1L, 10L)).thenReturn(4);
        when(messageMapper.selectList(any())).thenReturn(List.of());

        service.truncateMessages(10L, 200L, 1L, 10L);

        assertThat(conversation.getLastMessageAt()).isEqualTo(OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
    }

    @Test
    void truncateOtherUsersConversationIsNotFoundAndDoesNotDelete() {
        when(conversationMapper.selectById(1L)).thenReturn(owned(1L, 10L, 200L, "t"));

        assertThatThrownBy(() -> service.truncateMessages(10L, 999L, 1L, 50L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
        verifyNoInteractions(messageMapper);
    }

    @Test
    void truncateMessageFromAnotherConversationIsNotFound() {
        when(conversationMapper.selectById(1L)).thenReturn(owned(1L, 10L, 200L, "t"));
        AgentAiMessageEntity foreign = new AgentAiMessageEntity();
        foreign.setId(50L);
        foreign.setConversationId(2L);
        when(messageMapper.selectById(50L)).thenReturn(foreign);

        assertThatThrownBy(() -> service.truncateMessages(10L, 200L, 1L, 50L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
        verify(messageMapper, never()).deleteFromMessage(anyLong(), anyLong());
    }

    @Test
    void createShareBuildsSnapshotWithoutImageData() {
        when(conversationMapper.selectById(1L)).thenReturn(owned(1L, 10L, 200L, "標題"));
        AgentAiMessageEntity user = new AgentAiMessageEntity();
        user.setId(7L);
        user.setConversationId(1L);
        user.setRole(AgentAiMessageEntity.ROLE_USER);
        user.setContent("問題");
        user.setAttachments(
                "[{\"mediaType\":\"image/png\",\"data\":\"SECRET\",\"kind\":\"IMAGE\"},"
                        + "{\"mediaType\":\"application/pdf\",\"data\":null,\"fileName\":\"a.pdf\",\"kind\":\"DOCUMENT\"}]");
        AgentAiMessageEntity assistant = new AgentAiMessageEntity();
        assistant.setId(8L);
        assistant.setConversationId(1L);
        assistant.setRole(AgentAiMessageEntity.ROLE_ASSISTANT);
        assistant.setContent("回答");
        when(messageMapper.selectList(any())).thenReturn(List.of(user, assistant));
        when(shareMapper.selectOne(any())).thenReturn(null);

        AgentAiShareDto dto = service.createOrRefreshShare(10L, 200L, 1L);

        assertThat(dto.token()).isNotBlank();
        org.mockito.ArgumentCaptor<AgentAiConversationShareEntity> captor =
                org.mockito.ArgumentCaptor.forClass(AgentAiConversationShareEntity.class);
        verify(shareMapper).insert(captor.capture());
        assertThat(captor.getValue().getSnapshot())
                .contains("問題")
                .contains("回答")
                .contains("a.pdf")
                .contains("\"imageCount\":1")
                .doesNotContain("SECRET");
    }

    @Test
    void refreshShareReusesTokenAndUpdatesSnapshot() {
        when(conversationMapper.selectById(1L)).thenReturn(owned(1L, 10L, 200L, "t"));
        when(messageMapper.selectList(any())).thenReturn(List.of());
        AgentAiConversationShareEntity existing = new AgentAiConversationShareEntity();
        existing.setId(3L);
        existing.setConversationId(1L);
        existing.setToken("tok123");
        when(shareMapper.selectOne(any())).thenReturn(existing);

        AgentAiShareDto dto = service.createOrRefreshShare(10L, 200L, 1L);

        assertThat(dto.token()).isEqualTo("tok123");
        verify(shareMapper).updateById(existing);
        verify(shareMapper, never()).insert(any(AgentAiConversationShareEntity.class));
    }

    @Test
    void revokeOtherUsersShareIsNotFoundAndDoesNotDelete() {
        when(conversationMapper.selectById(1L)).thenReturn(owned(1L, 10L, 200L, "t"));

        assertThatThrownBy(() -> service.revokeShare(10L, 999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
        verify(shareMapper, never()).deleteByConversationId(anyLong());
    }

    @Test
    void sharedConversationReadsSnapshotByToken() {
        AgentAiConversationShareEntity share = new AgentAiConversationShareEntity();
        share.setId(3L);
        share.setToken("tok123");
        share.setSnapshot(
                "{\"title\":\"t\",\"sharedAt\":\"2026-01-01T00:00:00+09:00\",\"messages\":"
                        + "[{\"role\":\"USER\",\"content\":\"hi\",\"createdAt\":null,\"imageCount\":0,\"documentNames\":[]}]}");
        when(shareMapper.selectOne(any())).thenReturn(share);

        AgentAiSharedConversationDto dto = service.getSharedConversation("tok123");

        assertThat(dto.title()).isEqualTo("t");
        assertThat(dto.messages()).hasSize(1);
        assertThat(dto.messages().get(0).content()).isEqualTo("hi");
    }

    @Test
    void sharedConversationUnknownTokenIsNotFound() {
        when(shareMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getSharedConversation("nope"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void searchReturnsHitsWithSnippetAndConversationTitle() {
        AgentAiConversationEntity idOnly = new AgentAiConversationEntity();
        idOnly.setId(1L);
        when(conversationMapper.selectList(any())).thenReturn(List.of(idOnly));
        AgentAiMessageEntity hit = new AgentAiMessageEntity();
        hit.setId(88L);
        hit.setConversationId(1L);
        hit.setRole(AgentAiMessageEntity.ROLE_ASSISTANT);
        hit.setContent("前".repeat(50) + "關鍵內容" + "後".repeat(50));
        when(messageMapper.selectList(any())).thenReturn(List.of(hit));
        when(conversationMapper.selectBatchIds(any())).thenReturn(List.of(owned(1L, 10L, 200L, "標題甲")));

        var results = service.searchConversations(10L, 200L, "關鍵");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).conversationTitle()).isEqualTo("標題甲");
        assertThat(results.get(0).messageId()).isEqualTo(88L);
        assertThat(results.get(0).snippet()).contains("關鍵內容").startsWith("…").endsWith("…");
    }

    @Test
    void searchBlankQueryReturnsEmptyWithoutTouchingMappers() {
        assertThat(service.searchConversations(10L, 200L, "   ")).isEmpty();
        verifyNoInteractions(conversationMapper);
        verifyNoInteractions(messageMapper);
    }

    private static AgentAiConversationEntity owned(long id, long agentId, long agentUserId, String title) {
        AgentAiConversationEntity entity = new AgentAiConversationEntity();
        entity.setId(id);
        entity.setAgentId(agentId);
        entity.setAgentUserId(agentUserId);
        entity.setTitle(title);
        return entity;
    }
}
