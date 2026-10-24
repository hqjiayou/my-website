package com.mywebsite.site.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.mywebsite.site.entity.SiteAiConversationEntity;
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

    private SiteAiConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SiteAiConversationServiceImpl(
                conversationMapper, messageMapper, promptProfileMapper, new ObjectMapper());
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

    private static SiteAiConversationEntity owned(long id, long siteId, long siteUserId, String title) {
        SiteAiConversationEntity entity = new SiteAiConversationEntity();
        entity.setId(id);
        entity.setSiteId(siteId);
        entity.setSiteUserId(siteUserId);
        entity.setTitle(title);
        return entity;
    }
}
