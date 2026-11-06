package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.constant.AdminAiChainPresetMessageKeys;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainPresetDto;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainPresetUpdateRequest;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetCreateRequest;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetDto;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetUpdateRequest;
import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.entity.AdminAiChainPresetEntity;
import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.mapper.AdminAiChainPresetMapper;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 对话链预设服务（Issue #210 UoW-7）：成员归一化与存在性校验、被引用删除拦截、
 * agent 引用读写与组链读路径。
 */
@ExtendWith(MockitoExtension.class)
class AdminAiChainPresetServiceImplTest {

    @Mock
    private AdminAiChainPresetMapper chainPresetMapper;

    @Mock
    private AdminAiModelEntryMapper modelEntryMapper;

    @Mock
    private AdminAgentMapper agentMapper;

    private AdminAiChainPresetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAiChainPresetServiceImpl(
                chainPresetMapper, modelEntryMapper, agentMapper, new ObjectMapper());
    }

    private static AdminAiModelEntryEntity entry(long id) {
        AdminAiModelEntryEntity entry = new AdminAiModelEntryEntity();
        entry.setId(id);
        return entry;
    }

    private static AdminAiChainPresetEntity preset(long id, String entryIds) {
        AdminAiChainPresetEntity preset = new AdminAiChainPresetEntity();
        preset.setId(id);
        preset.setName("preset-" + id);
        preset.setPurpose("CHAT");
        preset.setEntryIds(entryIds);
        preset.setVersion(0);
        return preset;
    }

    private static AdminAgentEntity agent(long id, Long chainPresetId) {
        AdminAgentEntity agent = new AdminAgentEntity();
        agent.setId(id);
        agent.setChainPresetId(chainPresetId);
        agent.setVersion(0);
        return agent;
    }

    @Test
    void createPresetNormalizesMembersAndFixesPurposeToChat() {
        when(modelEntryMapper.selectBatchIds(any())).thenReturn(List.of(entry(3L), entry(1L)));
        List<AdminAiChainPresetEntity> savedHolder = new java.util.ArrayList<>();
        when(chainPresetMapper.insert(any(AdminAiChainPresetEntity.class)))
                .thenAnswer(invocation -> {
                    AdminAiChainPresetEntity entity = invocation.getArgument(0);
                    entity.setId(55L);
                    savedHolder.add(entity);
                    return 1;
                });
        when(chainPresetMapper.selectById(55L)).thenReturn(preset(55L, "[3,1]"));

        AdminAiChainPresetDto dto = service.createPreset(
                new AdminAiChainPresetCreateRequest(
                        " 主链 ", java.util.Arrays.asList(3L, 1L, 3L, null)));

        AdminAiChainPresetEntity saved = savedHolder.get(0);
        assertThat(saved.getName()).isEqualTo("主链");
        assertThat(saved.getPurpose()).isEqualTo("CHAT");
        // 保序去重、剔除 null，JSON 数组文本落库
        assertThat(saved.getEntryIds()).isEqualTo("[3,1]");
        assertThat(dto.entryIds()).containsExactly(3L, 1L);
    }

    @Test
    void createPresetRejectsMissingEntry() {
        when(modelEntryMapper.selectBatchIds(any())).thenReturn(List.of(entry(3L)));

        assertThatThrownBy(() ->
                        service.createPreset(new AdminAiChainPresetCreateRequest("x", List.of(3L, 99L))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> {
                            assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
                            assertThat(ex.getMessageKey())
                                    .isEqualTo(AdminAiChainPresetMessageKeys.ENTRY_NOT_FOUND);
                            assertThat(ex.getMessageArgs()).containsExactly(99L);
                        });
        verify(chainPresetMapper, never()).insert(any(AdminAiChainPresetEntity.class));
    }

    @Test
    void updatePresetDropsDanglingStoredMemberOnSave() {
        // 存量成员 1 已被删（批量查询只返 2）：改名保存应剔除 1 后成功，而非 400。
        when(modelEntryMapper.selectBatchIds(any())).thenReturn(List.of(entry(2L)));
        when(chainPresetMapper.selectById(7L)).thenReturn(preset(7L, "[1,2]"));
        List<AdminAiChainPresetEntity> savedHolder = new java.util.ArrayList<>();
        when(chainPresetMapper.updateById(any(AdminAiChainPresetEntity.class)))
                .thenAnswer(invocation -> {
                    savedHolder.add(invocation.getArgument(0));
                    return 1;
                });

        service.updatePreset(7L, new AdminAiChainPresetUpdateRequest("renamed", List.of(1L, 2L), 0));

        assertThat(savedHolder.get(0).getEntryIds()).isEqualTo("[2]");
    }

    @Test
    void updatePresetRejectsNewMissingMember() {
        // 请求新引入的不存在 id 3（不在存量成员里）仍须 ENTRY_NOT_FOUND。
        when(modelEntryMapper.selectBatchIds(any())).thenReturn(List.of(entry(2L)));
        when(chainPresetMapper.selectById(7L)).thenReturn(preset(7L, "[2]"));

        assertThatThrownBy(() -> service.updatePreset(
                        7L, new AdminAiChainPresetUpdateRequest("renamed", List.of(2L, 3L), 0)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> {
                            assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
                            assertThat(ex.getMessageKey())
                                    .isEqualTo(AdminAiChainPresetMessageKeys.ENTRY_NOT_FOUND);
                            assertThat(ex.getMessageArgs()).containsExactly(3L);
                        });
        verify(chainPresetMapper, never()).updateById(any(AdminAiChainPresetEntity.class));
    }

    @Test
    void updatePresetAppliesMembersAndReportsVersionConflict() {
        when(modelEntryMapper.selectBatchIds(any())).thenReturn(List.of(entry(2L)));
        when(chainPresetMapper.selectById(7L)).thenReturn(preset(7L, "[1]"));
        when(chainPresetMapper.updateById(any(AdminAiChainPresetEntity.class))).thenReturn(0);

        assertThatThrownBy(() -> service.updatePreset(
                        7L, new AdminAiChainPresetUpdateRequest("renamed", List.of(2L), 0)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode()));
    }

    @Test
    void deletePresetBlockedWhileReferenced() {
        when(chainPresetMapper.selectById(7L)).thenReturn(preset(7L, "[1]"));
        when(agentMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.deletePreset(7L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> {
                            assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode());
                            assertThat(ex.getMessageKey())
                                    .isEqualTo(AdminAiChainPresetMessageKeys.PRESET_REFERENCED);
                            assertThat(ex.getMessageArgs()).containsExactly(2L);
                        });
        verify(chainPresetMapper, never()).deleteById(any());
    }

    @Test
    void deletePresetSucceedsWhenUnreferenced() {
        when(chainPresetMapper.selectById(7L)).thenReturn(preset(7L, "[1]"));
        when(agentMapper.selectCount(any())).thenReturn(0L);

        service.deletePreset(7L);

        verify(chainPresetMapper).updateById(any(AdminAiChainPresetEntity.class));
        verify(chainPresetMapper).deleteById(7L);
    }

    @Test
    void agentChainPresetRoundTripAndClear() {
        AdminAgentEntity agent = agent(10L, null);
        when(agentMapper.selectById(10L)).thenReturn(agent);
        when(agentMapper.updateById(any(AdminAgentEntity.class))).thenReturn(1);
        when(chainPresetMapper.selectById(7L)).thenReturn(preset(7L, "[1]"));

        assertThat(service.getAgentChainPreset(10L).chainPresetId()).isNull();

        AdminAgentChainPresetDto updated =
                service.updateAgentChainPreset(10L, new AdminAgentChainPresetUpdateRequest(7L));
        assertThat(updated.chainPresetId()).isEqualTo(7L);
        assertThat(agent.getChainPresetId()).isEqualTo(7L);

        AdminAgentChainPresetDto cleared =
                service.updateAgentChainPreset(10L, new AdminAgentChainPresetUpdateRequest(null));
        assertThat(cleared.chainPresetId()).isNull();
    }

    @Test
    void updateAgentChainPresetRejectsMissingPresetAndAgent() {
        when(agentMapper.selectById(10L)).thenReturn(agent(10L, null));
        when(chainPresetMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() ->
                        service.updateAgentChainPreset(10L, new AdminAgentChainPresetUpdateRequest(99L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));

        when(agentMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> service.getAgentChainPreset(404L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void referencedEntryIdsFollowsAgentReference() {
        when(agentMapper.selectById(10L)).thenReturn(agent(10L, 7L));
        when(chainPresetMapper.selectById(7L)).thenReturn(preset(7L, "[8, 10, 9]"));

        assertThat(service.referencedEntryIds(10L)).containsExactly(8L, 10L, 9L);
    }

    @Test
    void referencedEntryIdsEmptyWithoutReferenceOrPreset() {
        when(agentMapper.selectById(10L)).thenReturn(agent(10L, null));
        when(agentMapper.selectById(20L)).thenReturn(agent(20L, 7L));
        when(chainPresetMapper.selectById(7L)).thenReturn(null);
        when(agentMapper.selectById(404L)).thenReturn(null);

        assertThat(service.referencedEntryIds(10L)).isEmpty();
        assertThat(service.referencedEntryIds(20L)).isEmpty();
        assertThat(service.referencedEntryIds(404L)).isEmpty();
    }
}
