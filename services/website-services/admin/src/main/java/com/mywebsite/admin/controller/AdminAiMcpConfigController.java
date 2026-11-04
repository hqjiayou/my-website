package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiMcpConfigDtos.AdminAiMcpConfigDto;
import com.mywebsite.admin.dto.AdminAiMcpConfigDtos.AdminAiMcpConfigUpdateRequest;
import com.mywebsite.admin.service.AdminAiMcpConfigService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP 服务器全局注册表（Issue #192 UoW-1）。与运行配置同口径挂权限
 * （读 ai-model:read、写 ai-model:update，沿用现行 AI 设置口径、不新造权限码）；
 * 响应永不含 headers/env/url 明文（只回 hint）。推送重试走运行配置端点的
 * retry-push（同一行、同一管线），本控制器不另设。
 */
@RestController
@RequestMapping("/api/admin/ai/mcp-config")
@Tag(name = "Admin · AI MCP Config")
@SecurityRequirement(name = "adminBearer")
public class AdminAiMcpConfigController {

    private final AdminAiMcpConfigService adminAiMcpConfigService;

    public AdminAiMcpConfigController(AdminAiMcpConfigService adminAiMcpConfigService) {
        this.adminAiMcpConfigService = adminAiMcpConfigService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "MCP 注册表视图：条目结构化回显，headers/env/url 只回 hint，附配置版本与最近推送状态")
    public ResponseEntity<HttpMessageBody<AdminAiMcpConfigDto>> get() {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAiMcpConfigService.getConfig()));
    }

    @PutMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:update')")
    @Operation(summary = "保存 MCP 注册表（条目整体替换、密钥留空保留、config_version +1）并立即推送 agent-services 热替换")
    public ResponseEntity<HttpMessageBody<AdminAiMcpConfigDto>> update(
            @Valid @RequestBody AdminAiMcpConfigUpdateRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAiMcpConfigService.updateConfig(request)));
    }
}
