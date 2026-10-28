package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiPromptDtos.AdminAiPromptProfileDto;
import com.mywebsite.admin.dto.AdminAiPromptDtos.AdminAiPromptProfileUpdateRequest;
import com.mywebsite.admin.service.AdminAiPromptService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sites/{agentId}/ai/prompt-profile")
@Tag(name = "Admin · AI Prompt")
@SecurityRequirement(name = "adminBearer")
public class AdminAiPromptController {

    private final AdminAiPromptService adminAiPromptService;

    public AdminAiPromptController(AdminAiPromptService adminAiPromptService) {
        this.adminAiPromptService = adminAiPromptService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "查看站点 AI Prompt 配置")
    public ResponseEntity<HttpMessageBody<AdminAiPromptProfileDto>> get(@PathVariable long agentId) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiPromptService.getPromptProfile(agentId)));
    }

    @PutMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:update')")
    @Operation(summary = "更新站点 AI Prompt 配置（不存在则创建）")
    public ResponseEntity<HttpMessageBody<AdminAiPromptProfileDto>> upsert(
            @PathVariable long agentId, @Valid @RequestBody AdminAiPromptProfileUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiPromptService.upsertPromptProfile(agentId, request)));
    }
}
