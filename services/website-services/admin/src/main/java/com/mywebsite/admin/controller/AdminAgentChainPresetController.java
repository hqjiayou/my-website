package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainPresetDto;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainPresetUpdateRequest;
import com.mywebsite.admin.service.AdminAiChainPresetService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * agent 对话链预设引用（Issue #210 UoW-7）：工作台读取/保存该 agent 引用的
 * 链预设，保存即换链（正式对话、试聊、OCR、提炼同口径随之变化）。权限码与
 * 平台区链预设同口径（{@code admin:ai-model:read}）。
 */
@RestController
@RequestMapping("/api/admin/agents/{agentId}/ai/chain-preset")
@Tag(name = "Admin · Agent AI Chain Preset")
@SecurityRequirement(name = "adminBearer")
public class AdminAgentChainPresetController {

    private final AdminAiChainPresetService chainPresetService;

    public AdminAgentChainPresetController(AdminAiChainPresetService chainPresetService) {
        this.chainPresetService = chainPresetService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "查看 agent 当前引用的对话链预设")
    public ResponseEntity<HttpMessageBody<AdminAgentChainPresetDto>> get(@PathVariable long agentId) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, chainPresetService.getAgentChainPreset(agentId)));
    }

    @PutMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "保存 agent 的对话链预设引用（保存即换链；null 为解除引用）")
    public ResponseEntity<HttpMessageBody<AdminAgentChainPresetDto>> update(
            @PathVariable long agentId, @RequestBody AdminAgentChainPresetUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, chainPresetService.updateAgentChainPreset(agentId, request)));
    }
}
