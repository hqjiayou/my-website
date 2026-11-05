package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiConfigPresetDtos.AdminAiConfigPresetCreateRequest;
import com.mywebsite.admin.dto.AdminAiConfigPresetDtos.AdminAiConfigPresetDto;
import com.mywebsite.admin.dto.AdminAiConfigPresetDtos.AdminAiConfigPresetUpdateRequest;
import java.util.List;

/** AI 配置预设 CRUD（Issue #211 UoW-8）：预设是多 agent 共享资源，删除只软删预设本身，不级联 agent 引用。 */
public interface AdminAiConfigPresetService {

    List<AdminAiConfigPresetDto> listPresets();

    AdminAiConfigPresetDto getPreset(long id);

    AdminAiConfigPresetDto createPreset(AdminAiConfigPresetCreateRequest request);

    AdminAiConfigPresetDto updatePreset(long id, AdminAiConfigPresetUpdateRequest request);

    void softDeletePreset(long id);
}
