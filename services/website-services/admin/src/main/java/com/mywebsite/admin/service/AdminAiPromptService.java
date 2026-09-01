package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiPromptDtos.AdminAiPromptProfileDto;
import com.mywebsite.admin.dto.AdminAiPromptDtos.AdminAiPromptProfileUpdateRequest;

public interface AdminAiPromptService {

    AdminAiPromptProfileDto getPromptProfile(long siteId);

    AdminAiPromptProfileDto upsertPromptProfile(long siteId, AdminAiPromptProfileUpdateRequest request);
}
