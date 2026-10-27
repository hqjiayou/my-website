package com.mywebsite.site.service;

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
import com.mywebsite.site.dto.SiteAiConversationDto;
import com.mywebsite.site.dto.SiteAiShareDto;
import com.mywebsite.site.dto.SiteAiSharedConversationDto;
import com.mywebsite.site.entity.SiteAiConversationEntity;
import com.mywebsite.site.entity.SiteAiConversationShareEntity;
import com.mywebsite.site.entity.SiteAiMessageEntity;
import com.mywebsite.site.mapper.SiteAiConversationShareMapper;
import java.time.LocalDateTime;
import java.util.List;
import com.mywebsite.site.mapper.SiteAiConversationMapper;
import com.mywebsite.site.mapper.SiteAiMessageMapper;
import com.mywebsite.site.mapper.SiteAiPromptProfileMapper;
import com.mywebsite.site.service.impl.SiteAiConversationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 会话重命名与硬删。Mapper 用 mock，才能断言非本人路径删除方法零调用。 */
@ExtendWith(MockitoExtension.class)
class SiteAiConversationManageTest {

    @Mock
    private SiteAiConversationMapper conversationMapper;

    @Mock
    private SiteAiMessageMapper messageMapper;

    @Mock
    private SiteAiPromptProfileMapper promptProfileMapper;

    @Mock
    private SiteAiConversationShareMapper shareMapper;

    private SiteAiConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SiteAiConversationServiceImpl(
                conversationMapper, messageMapper, promptProfileMapper, shareMapper, new ObjectMapper());
    }

    @Test
    void renameOwnConversationTrimsTitle() {
        SiteAiConversationEntity entity = owned(1L, 10L, 200L, "old");
        when(conversationMapper.selectById(1L)).thenReturn(entity);

        SiteAiConversationDto dto = service.renameConversation(10L, 200L, 1L, "  new title  ");

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
        SiteAiConversationEntity conversation = owned(1L, 10L, 200L, "t");
        conversation.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        conversation.setAgentTodos("[{\"text\":\"x\"}]");
        SiteAiMessageEntity target = new SiteAiMessageEntity();
        target.setId(50L);
        target.setConversationId(1L);
        SiteAiMessageEntity remaining = new SiteAiMessageEntity();
        remaining.setId(40L);
        remaining.setConversationId(1L);
        remaining.setCreatedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
        when(conversationMapper.selectById(1L)).thenReturn(conversation);
        when(messageMapper.selectById(50L)).thenReturn(target);
        when(messageMapper.deleteFromMessage(1L, 50L)).thenReturn(2);
        when(messageMapper.selectList(any())).thenReturn(List.of(remaining));

        service.truncateMessages(10L, 200L, 1L, 50L);

        verify(messageMapper).deleteFromMessage(1L, 50L);
        assertThat(conversation.getLastMessageAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
        assertThat(conversation.getAgentTodos()).isNull();
        verify(conversationMapper).updateById(conversation);
    }

    @Test
    void truncateAllMessagesFallsBackToConversationCreatedAt() {
        SiteAiConversationEntity conversation = owned(1L, 10L, 200L, "t");
        conversation.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        SiteAiMessageEntity target = new SiteAiMessageEntity();
        target.setId(10L);
        target.setConversationId(1L);
        when(conversationMapper.selectById(1L)).thenReturn(conversation);
        when(messageMapper.selectById(10L)).thenReturn(target);
        when(messageMapper.deleteFromMessage(1L, 10L)).thenReturn(4);
        when(messageMapper.selectList(any())).thenReturn(List.of());

        service.truncateMessages(10L, 200L, 1L, 10L);

        assertThat(conversation.getLastMessageAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
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
        SiteAiMessageEntity foreign = new SiteAiMessageEntity();
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
        SiteAiMessageEntity user = new SiteAiMessageEntity();
        user.setId(7L);
        user.setConversationId(1L);
        user.setRole(SiteAiMessageEntity.ROLE_USER);
        user.setContent("問題");
        user.setAttachments(
                "[{\"mediaType\":\"image/png\",\"data\":\"SECRET\",\"kind\":\"IMAGE\"},"
                        + "{\"mediaType\":\"application/pdf\",\"data\":null,\"fileName\":\"a.pdf\",\"kind\":\"DOCUMENT\"}]");
        SiteAiMessageEntity assistant = new SiteAiMessageEntity();
        assistant.setId(8L);
        assistant.setConversationId(1L);
        assistant.setRole(SiteAiMessageEntity.ROLE_ASSISTANT);
        assistant.setContent("回答");
        when(messageMapper.selectList(any())).thenReturn(List.of(user, assistant));
        when(shareMapper.selectOne(any())).thenReturn(null);

        SiteAiShareDto dto = service.createOrRefreshShare(10L, 200L, 1L);

        assertThat(dto.token()).isNotBlank();
        org.mockito.ArgumentCaptor<SiteAiConversationShareEntity> captor =
                org.mockito.ArgumentCaptor.forClass(SiteAiConversationShareEntity.class);
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
        SiteAiConversationShareEntity existing = new SiteAiConversationShareEntity();
        existing.setId(3L);
        existing.setConversationId(1L);
        existing.setToken("tok123");
        when(shareMapper.selectOne(any())).thenReturn(existing);

        SiteAiShareDto dto = service.createOrRefreshShare(10L, 200L, 1L);

        assertThat(dto.token()).isEqualTo("tok123");
        verify(shareMapper).updateById(existing);
        verify(shareMapper, never()).insert(any());
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
        SiteAiConversationShareEntity share = new SiteAiConversationShareEntity();
        share.setId(3L);
        share.setToken("tok123");
        share.setSnapshot(
                "{\"title\":\"t\",\"sharedAt\":\"2026-01-01T00:00:00+09:00\",\"messages\":"
                        + "[{\"role\":\"USER\",\"content\":\"hi\",\"createdAt\":null,\"imageCount\":0,\"documentNames\":[]}]}");
        when(shareMapper.selectOne(any())).thenReturn(share);

        SiteAiSharedConversationDto dto = service.getSharedConversation("tok123");

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

    private static SiteAiConversationEntity owned(long id, long siteId, long siteUserId, String title) {
        SiteAiConversationEntity entity = new SiteAiConversationEntity();
        entity.setId(id);
        entity.setSiteId(siteId);
        entity.setSiteUserId(siteUserId);
        entity.setTitle(title);
        return entity;
    }
}
