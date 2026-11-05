package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.dto.AdminAiConfigPresetDtos.AdminAiConfigPresetCreateRequest;
import com.mywebsite.admin.dto.AdminAiConfigPresetDtos.AdminAiConfigPresetDto;
import com.mywebsite.admin.dto.AdminAiConfigPresetDtos.AdminAiConfigPresetUpdateRequest;
import com.mywebsite.admin.entity.AdminAiConfigPresetEntity;
import com.mywebsite.admin.mapper.AdminAiConfigPresetMapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 配置预设 CRUD（Issue #211 UoW-8）：默认集 JSON 往返保序、空集存 NULL、版本冲突与软删口径。 */
@ExtendWith(MockitoExtension.class)
class AdminAiConfigPresetServiceTest {

    @Mock
    private AdminAiConfigPresetMapper configPresetMapper;

    private AdminAiConfigPresetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAiConfigPresetServiceImpl(configPresetMapper, new ObjectMapper());
    }

    @Test
    void createStoresSkillIdsAsOrderedJsonAndReturnsThem() {
        when(configPresetMapper.insert(any(AdminAiConfigPresetEntity.class)))
                .thenAnswer(invocation -> {
                    AdminAiConfigPresetEntity entity = invocation.getArgument(0);
                    entity.setId(5L);
                    return 1;
                });
        AdminAiConfigPresetEntity stored = preset(5L, "预设甲", "提示", "0.50", "[\"b-skill\",\"a-skill\"]");
        when(configPresetMapper.selectById(5L)).thenReturn(stored);

        AdminAiConfigPresetDto dto = service.createPreset(new AdminAiConfigPresetCreateRequest(
                " 预设甲 ", "提示", new BigDecimal("0.50"), List.of("b-skill", "a-skill")));

        ArgumentCaptor<AdminAiConfigPresetEntity> captor =
                ArgumentCaptor.forClass(AdminAiConfigPresetEntity.class);
        verify(configPresetMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("预设甲");
        assertThat(captor.getValue().getDefaultSkillIds()).isEqualTo("[\"b-skill\",\"a-skill\"]");
        assertThat(dto.defaultSkillIds()).containsExactly("b-skill", "a-skill");
        assertThat(dto.temperature()).isEqualByComparingTo("0.50");
    }

    @Test
    void createWithEmptySkillSetStoresNull() {
        when(configPresetMapper.insert(any(AdminAiConfigPresetEntity.class)))
                .thenAnswer(invocation -> {
                    AdminAiConfigPresetEntity entity = invocation.getArgument(0);
                    entity.setId(6L);
                    return 1;
                });
        when(configPresetMapper.selectById(6L)).thenReturn(preset(6L, "预设乙", null, null, null));

        AdminAiConfigPresetDto dto = service.createPreset(
                new AdminAiConfigPresetCreateRequest("预设乙", null, null, List.of()));

        ArgumentCaptor<AdminAiConfigPresetEntity> captor =
                ArgumentCaptor.forClass(AdminAiConfigPresetEntity.class);
        verify(configPresetMapper).insert(captor.capture());
        assertThat(captor.getValue().getDefaultSkillIds()).isNull();
        assertThat(dto.defaultSkillIds()).isEmpty();
    }

    @Test
    void getMissingPresetIsNotFound() {
        when(configPresetMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.getPreset(404L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void updateAppliesFieldsAndSkillSet() {
        AdminAiConfigPresetEntity existing = preset(7L, "旧名", "旧提示", null, null);
        existing.setVersion(3);
        when(configPresetMapper.selectById(7L)).thenReturn(existing);
        when(configPresetMapper.updateById(existing)).thenReturn(1);

        AdminAiConfigPresetDto dto = service.updatePreset(
                7L,
                new AdminAiConfigPresetUpdateRequest(
                        "新名", "新提示", new BigDecimal("1.20"), List.of("s1"), 3));

        assertThat(existing.getName()).isEqualTo("新名");
        assertThat(existing.getDefaultSkillIds()).isEqualTo("[\"s1\"]");
        assertThat(dto.name()).isEqualTo("新名");
        assertThat(dto.defaultSkillIds()).containsExactly("s1");
    }

    @Test
    void updateWithStaleVersionConflicts() {
        AdminAiConfigPresetEntity existing = preset(7L, "旧名", "旧提示", null, null);
        when(configPresetMapper.selectById(7L)).thenReturn(existing);
        when(configPresetMapper.updateById(existing)).thenReturn(0);

        assertThatThrownBy(() -> service.updatePreset(
                        7L, new AdminAiConfigPresetUpdateRequest("新名", null, null, null, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.CONFLICT.getCode());
    }

    @Test
    void softDeleteStampsDeletedAtAndDeletesById() {
        AdminAiConfigPresetEntity existing = preset(8L, "待删", null, null, null);
        when(configPresetMapper.selectById(8L)).thenReturn(existing);

        service.softDeletePreset(8L);

        assertThat(existing.getDeletedAt()).isNotNull();
        verify(configPresetMapper).updateById(existing);
        verify(configPresetMapper).deleteById(8L);
    }

    @Test
    void malformedStoredSkillIdsReadAsEmpty() {
        when(configPresetMapper.selectById(9L)).thenReturn(preset(9L, "脏数据", null, null, "{not-json"));

        assertThat(service.getPreset(9L).defaultSkillIds()).isEmpty();
    }

    private static AdminAiConfigPresetEntity preset(
            Long id, String name, String systemPrompt, String temperature, String defaultSkillIds) {
        AdminAiConfigPresetEntity preset = new AdminAiConfigPresetEntity();
        preset.setId(id);
        preset.setName(name);
        preset.setSystemPrompt(systemPrompt);
        preset.setTemperature(temperature == null ? null : new BigDecimal(temperature));
        preset.setDefaultSkillIds(defaultSkillIds);
        preset.setVersion(0);
        return preset;
    }
}
