package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiPromptDtos.AdminAiPromptProfileDto;
import com.mywebsite.admin.dto.AdminAiPromptDtos.AdminAiPromptProfileUpdateRequest;

public interface AdminAiPromptService {

    AdminAiPromptProfileDto getPromptProfile(long agentId);

    AdminAiPromptProfileDto upsertPromptProfile(long agentId, AdminAiPromptProfileUpdateRequest request);

    void softDeleteByAgentId(long agentId);
}
