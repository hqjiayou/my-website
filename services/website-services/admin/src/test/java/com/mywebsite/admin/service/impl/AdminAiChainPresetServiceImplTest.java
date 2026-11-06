package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.constant.AdminAiChainPresetMessageKeys;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainDto;
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
 * 对话链服务（Issue #210 UoW-7 引入，#214 UoW-3 链内置化）：模板库 CRUD
 * （成员归一化与存在性校验、保存剔除悬空成员、删除无引用拦截）与 agent
 * 自有链读写（按模式整表保存、模式一致性校验、模板载入是拷贝、另存为模板、
 * 组链读路径）。
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
        return entry(id, null);
    }

    private static AdminAiModelEntryEntity entry(long id, String mode) {
        AdminAiModelEntryEntity entry = new AdminAiModelEntryEntity();
        entry.setId(id);
        entry.setMode(mode);
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

    private static AdminAgentEntity agent(long id, String keyEntryIds, String cliEntryIds) {
        AdminAgentEntity agent = new AdminAgentEntity();
        agent.setId(id);
        agent.setChainKeyEntryIds(keyEntryIds);
        agent.setChainCliEntryIds(cliEntryIds);
        agent.setVersion(0);
        return agent;
    }

    // ------------------------------------------------------------------
    // 模板库
    // ------------------------------------------------------------------

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
    void deletePresetSucceedsWithoutReferenceGuard() {
        // 链内置化后模板不被引用（套用是拷贝）：删除不再查引用数、不再拦截。
        when(chainPresetMapper.selectById(7L)).thenReturn(preset(7L, "[1]"));

        service.deletePreset(7L);

        verify(chainPresetMapper).updateById(any(AdminAiChainPresetEntity.class));
        verify(chainPresetMapper).deleteById(7L);
        verify(agentMapper, never()).selectCount(any());
    }

    // ------------------------------------------------------------------
    // agent 自有链
    // ------------------------------------------------------------------

    @Test
    void getAgentChainReportsBothModes() {
        when(agentMapper.selectById(10L)).thenReturn(agent(10L, "[3, 1]", "[5]"));

        AdminAgentChainDto dto = service.getAgentChain(10L);

        assertThat(dto.agentId()).isEqualTo(10L);
        assertThat(dto.keyEntryIds()).containsExactly(3L, 1L);
        assertThat(dto.cliEntryIds()).containsExactly(5L);
    }

    @Test
    void updateAgentChainWritesNormalizedMembersIntoModeColumn() {
        AdminAgentEntity agent = agent(10L, null, "[7]");
        when(agentMapper.selectById(10L)).thenReturn(agent);
        when(agentMapper.updateById(any(AdminAgentEntity.class))).thenReturn(1);
        when(modelEntryMapper.selectBatchIds(any()))
                .thenReturn(List.of(entry(3L, "KEY"), entry(1L, "KEY")));

        AdminAgentChainDto dto =
                service.updateAgentChain(10L, "key", java.util.Arrays.asList(3L, 1L, 3L, null));

        // 保序去重落到 KEY 列；CLI 列原样不动
        assertThat(agent.getChainKeyEntryIds()).isEqualTo("[3,1]");
        assertThat(agent.getChainCliEntryIds()).isEqualTo("[7]");
        assertThat(dto.keyEntryIds()).containsExactly(3L, 1L);
        assertThat(dto.cliEntryIds()).containsExactly(7L);
    }

    @Test
    void updateAgentChainWithEmptyListClearsColumnToNull() {
        AdminAgentEntity agent = agent(10L, "[3]", null);
        when(agentMapper.selectById(10L)).thenReturn(agent);
        when(agentMapper.updateById(any(AdminAgentEntity.class))).thenReturn(1);

        AdminAgentChainDto dto = service.updateAgentChain(10L, "KEY", List.of());

        assertThat(agent.getChainKeyEntryIds()).isNull();
        assertThat(dto.keyEntryIds()).isEmpty();
    }

    @Test
    void updateAgentChainRejectsMissingEntry() {
        when(agentMapper.selectById(10L)).thenReturn(agent(10L, null, null));
        when(modelEntryMapper.selectBatchIds(any())).thenReturn(List.of(entry(3L, "KEY")));

        assertThatThrownBy(() -> service.updateAgentChain(10L, "KEY", List.of(3L, 99L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> {
                            assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
                            assertThat(ex.getMessageKey())
                                    .isEqualTo(AdminAiChainPresetMessageKeys.ENTRY_NOT_FOUND);
                            assertThat(ex.getMessageArgs()).containsExactly(99L);
                        });
        verify(agentMapper, never()).updateById(any(AdminAgentEntity.class));
    }

    @Test
    void updateAgentChainRejectsEntryOfOtherMode() {
        when(agentMapper.selectById(10L)).thenReturn(agent(10L, null, null));
        when(modelEntryMapper.selectBatchIds(any())).thenReturn(List.of(entry(5L, "CLI")));

        assertThatThrownBy(() -> service.updateAgentChain(10L, "KEY", List.of(5L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> {
                            assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
                            assertThat(ex.getMessageKey())
                                    .isEqualTo(AdminAiChainPresetMessageKeys.ENTRY_MODE_MISMATCH);
                            assertThat(ex.getMessageArgs()).containsExactly(5L);
                        });
        verify(agentMapper, never()).updateById(any(AdminAgentEntity.class));
    }

    @Test
    void updateAgentChainRejectsUnknownModeAndMissingAgent() {
        // 模式校验先于 agent 加载（requireMode 在 requireAgent 之前），故此处不打 agent 桩。
        assertThatThrownBy(() -> service.updateAgentChain(10L, "BOGUS", List.of()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode()));

        when(agentMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> service.getAgentChain(404L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void ownChainEntryIdsReadsPerModeAndMergesForWholeChain() {
        when(agentMapper.selectById(10L)).thenReturn(agent(10L, "[8, 9]", "[10]"));
        when(agentMapper.selectById(404L)).thenReturn(null);

        assertThat(service.ownChainEntryIds(10L, "KEY")).containsExactly(8L, 9L);
        assertThat(service.ownChainEntryIds(10L, "CLI")).containsExactly(10L);
        // 整链定义：KEY 在前、CLI 在后（见服务接口注释）
        assertThat(service.ownChainEntryIds(10L, null)).containsExactly(8L, 9L, 10L);
        assertThat(service.ownChainEntryIds(404L, "KEY")).isEmpty();
    }

    @Test
    void loadAgentChainFromTemplateCopiesOnlyTargetModeMembers() {
        AdminAgentEntity agent = agent(10L, null, "[42]");
        when(agentMapper.selectById(10L)).thenReturn(agent);
        when(agentMapper.updateById(any(AdminAgentEntity.class))).thenReturn(1);
        when(chainPresetMapper.selectById(7L)).thenReturn(preset(7L, "[8, 10, 9, 11]"));
        when(modelEntryMapper.selectBatchIds(any()))
                .thenReturn(List.of(
                        entry(8L, "KEY"), entry(10L, "CLI"), entry(9L, "KEY"), entry(11L, "CLI")));

        AdminAgentChainDto dto = service.loadAgentChainFromTemplate(10L, "KEY", 7L);

        // 只拷模板中属于 KEY 的成员且保模板序；CLI 自有链不动
        assertThat(agent.getChainKeyEntryIds()).isEqualTo("[8,9]");
        assertThat(agent.getChainCliEntryIds()).isEqualTo("[42]");
        assertThat(dto.keyEntryIds()).containsExactly(8L, 9L);
        assertThat(dto.cliEntryIds()).containsExactly(42L);
    }

    @Test
    void templateLoadIsACopyEditingTemplateAfterwardsDoesNotChangeAgentChain() {
        AdminAgentEntity agent = agent(10L, null, null);
        AdminAiChainPresetEntity template = preset(7L, "[8, 10, 9, 11]");
        when(agentMapper.selectById(10L)).thenReturn(agent);
        when(agentMapper.updateById(any(AdminAgentEntity.class))).thenReturn(1);
        when(chainPresetMapper.selectById(7L)).thenReturn(template);
        when(chainPresetMapper.updateById(any(AdminAiChainPresetEntity.class))).thenReturn(1);
        when(modelEntryMapper.selectBatchIds(any()))
                .thenReturn(List.of(
                        entry(8L, "KEY"), entry(10L, "CLI"), entry(9L, "KEY"), entry(11L, "CLI")));

        service.loadAgentChainFromTemplate(10L, "KEY", 7L);
        assertThat(service.ownChainEntryIds(10L, "KEY")).containsExactly(8L, 9L);

        // 之后把模板改成只剩 11：agent 已拷贝的链纹丝不动（拷贝而非引用）
        service.updatePreset(7L, new AdminAiChainPresetUpdateRequest("renamed", List.of(11L), 0));
        assertThat(template.getEntryIds()).isEqualTo("[11]");
        assertThat(service.ownChainEntryIds(10L, "KEY")).containsExactly(8L, 9L);
    }

    @Test
    void saveAgentChainAsTemplateCreatesPresetWithThatModesMembers() {
        when(agentMapper.selectById(10L)).thenReturn(agent(10L, "[8, 9]", "[10]"));
        List<AdminAiChainPresetEntity> savedHolder = new java.util.ArrayList<>();
        when(chainPresetMapper.insert(any(AdminAiChainPresetEntity.class)))
                .thenAnswer(invocation -> {
                    AdminAiChainPresetEntity entity = invocation.getArgument(0);
                    entity.setId(56L);
                    savedHolder.add(entity);
                    return 1;
                });
        when(chainPresetMapper.selectById(56L)).thenReturn(preset(56L, "[10]"));

        AdminAiChainPresetDto dto = service.saveAgentChainAsTemplate(10L, "CLI", " cli 模板 ");

        AdminAiChainPresetEntity saved = savedHolder.get(0);
        assertThat(saved.getName()).isEqualTo("cli 模板");
        assertThat(saved.getPurpose()).isEqualTo("CHAT");
        // 只取 CLI 模式链的内容，KEY 链不混入
        assertThat(saved.getEntryIds()).isEqualTo("[10]");
        assertThat(dto.entryIds()).containsExactly(10L);
    }
}
