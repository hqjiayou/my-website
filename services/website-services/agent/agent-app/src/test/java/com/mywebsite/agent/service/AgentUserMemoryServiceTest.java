package com.mywebsite.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.agent.dto.AgentUserMemoryDto;
import com.mywebsite.agent.entity.AgentUserMemoryEntity;
import com.mywebsite.agent.mapper.AgentUserMemoryMapper;
import com.mywebsite.agent.service.impl.AgentUserMemoryServiceImpl;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 记忆落库、去重、50 条淘汰，以及只操作本人的删除。 */
@ExtendWith(MockitoExtension.class)
class AgentUserMemoryServiceTest {

    @Mock
    private AgentUserMemoryMapper memoryMapper;

    private AgentUserMemoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentUserMemoryServiceImpl(memoryMapper);
    }

    @Test
    void recentContentsAsksForTwentyAndRestoresChronologicalOrder() {
        when(memoryMapper.selectRecentContents(10L, 200L, AgentUserMemoryServiceImpl.INJECT_LIMIT))
                .thenReturn(List.of("newer", "older"));

        assertThat(service.recentContents(10L, 200L)).containsExactly("older", "newer");
    }

    @Test
    void storeNormalizesWhitespaceAndRecordsConversation() {
        when(memoryMapper.selectActiveContents(10L, 200L)).thenReturn(List.of());
        when(memoryMapper.countActive(10L, 200L)).thenReturn(0L);

        service.storeExtracted(10L, 200L, 9L, List.of("  hello   world \n"));

        ArgumentCaptor<AgentUserMemoryEntity> captor = ArgumentCaptor.forClass(AgentUserMemoryEntity.class);
        verify(memoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("hello world");
        assertThat(captor.getValue().getSourceConversationId()).isEqualTo(9L);
        assertThat(captor.getValue().getAgentId()).isEqualTo(10L);
        assertThat(captor.getValue().getUserId()).isEqualTo(200L);
    }

    @Test
    void storeSkipsBlankAndExactDuplicatesWithoutEvicting() {
        service.storeExtracted(10L, 200L, 9L, null);
        service.storeExtracted(10L, 200L, 9L, List.of());
        service.storeExtracted(10L, 200L, 9L, List.of("   ", "\n"));
        verifyNoInteractions(memoryMapper);

        when(memoryMapper.selectActiveContents(10L, 200L)).thenReturn(List.of("User likes tea."));
        service.storeExtracted(10L, 200L, 9L, List.of("User likes tea.", "  User likes tea.  "));

        verify(memoryMapper, never()).insert(any(AgentUserMemoryEntity.class));
        verify(memoryMapper, never()).countActive(anyLong(), anyLong());
        verify(memoryMapper, never()).deleteById(anyLong());
    }

    @Test
    void storeEvictsOldestWhenAlreadyAtFifty() {
        List<String> existing = new ArrayList<>();
        for (int i = 0; i < AgentUserMemoryServiceImpl.STORE_LIMIT; i++) {
            existing.add("old-" + i);
        }
        AtomicInteger count = new AtomicInteger(AgentUserMemoryServiceImpl.STORE_LIMIT);
        Deque<Long> oldestFirst = new ArrayDeque<>();
        for (long id = 1; id <= AgentUserMemoryServiceImpl.STORE_LIMIT; id++) {
            oldestFirst.add(id);
        }
        when(memoryMapper.selectActiveContents(10L, 200L)).thenReturn(existing);
        when(memoryMapper.countActive(10L, 200L)).thenAnswer(invocation -> (long) count.get());
        when(memoryMapper.selectOldestId(10L, 200L)).thenAnswer(invocation -> oldestFirst.peekFirst());
        when(memoryMapper.selectById(anyLong())).thenAnswer(invocation -> {
            AgentUserMemoryEntity entity = new AgentUserMemoryEntity();
            entity.setId(invocation.getArgument(0));
            entity.setAgentId(10L);
            entity.setUserId(200L);
            return entity;
        });
        when(memoryMapper.updateById(any(AgentUserMemoryEntity.class))).thenReturn(1);
        when(memoryMapper.deleteById(anyLong())).thenAnswer(invocation -> {
            oldestFirst.remove(invocation.getArgument(0));
            count.decrementAndGet();
            return 1;
        });
        when(memoryMapper.insert(any(AgentUserMemoryEntity.class))).thenAnswer(invocation -> {
            count.incrementAndGet();
            return 1;
        });

        service.storeExtracted(10L, 200L, 7L, List.of("old-0", "  fresh   fact  ", "fresh fact"));

        assertThat(count.get()).isEqualTo(AgentUserMemoryServiceImpl.STORE_LIMIT);
        assertThat(oldestFirst).doesNotContain(1L).contains(2L);
        ArgumentCaptor<AgentUserMemoryEntity> captor = ArgumentCaptor.forClass(AgentUserMemoryEntity.class);
        verify(memoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("fresh fact");
        assertThat(captor.getValue().getSourceConversationId()).isEqualTo(7L);
        verify(memoryMapper).deleteById(1L);
    }

    @Test
    void deleteOtherUsersMemoryIsNotFound() {
        when(memoryMapper.selectById(5L)).thenReturn(owned(5L, 10L, 200L, "secret"));

        assertThatThrownBy(() -> service.deleteOwn(10L, 999L, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
        verify(memoryMapper, never()).deleteById(anyLong());
        verify(memoryMapper, never()).updateById(any(AgentUserMemoryEntity.class));
    }

    @Test
    void clearOnlyDeletesRowsReturnedForThatUser() {
        AgentUserMemoryEntity mine = owned(1L, 10L, 200L, "mine");
        when(memoryMapper.selectOwn(10L, 200L)).thenReturn(List.of(mine));
        when(memoryMapper.updateById(any(AgentUserMemoryEntity.class))).thenReturn(1);
        when(memoryMapper.deleteById(1L)).thenReturn(1);

        service.clearOwn(10L, 200L);

        verify(memoryMapper).selectOwn(10L, 200L);
        verify(memoryMapper, never()).selectOwn(10L, 999L);
        verify(memoryMapper).deleteById(1L);
        verify(memoryMapper, never()).deleteById(2L);
    }

    @Test
    void listOwnMapsIdContentAndCreatedAt() {
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 10, 26, 1, 2, 3, 0, ZoneOffset.UTC);
        AgentUserMemoryEntity row = owned(4L, 10L, 200L, "likes tea");
        row.setCreatedAt(createdAt);
        when(memoryMapper.selectOwn(10L, 200L)).thenReturn(List.of(row));

        List<AgentUserMemoryDto> listed = service.listOwn(10L, 200L);

        assertThat(listed).containsExactly(new AgentUserMemoryDto(4L, "likes tea", createdAt));
    }

    private static AgentUserMemoryEntity owned(long id, long agentId, long userId, String content) {
        AgentUserMemoryEntity entity = new AgentUserMemoryEntity();
        entity.setId(id);
        entity.setAgentId(agentId);
        entity.setUserId(userId);
        entity.setContent(content);
        return entity;
    }
}
