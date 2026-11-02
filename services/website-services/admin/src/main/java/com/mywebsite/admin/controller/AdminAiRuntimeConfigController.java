package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiRuntimeConfigDtos.AdminAiRuntimeConfigDto;
import com.mywebsite.admin.dto.AdminAiRuntimeConfigDtos.AdminAiRuntimeConfigUpdateRequest;
import com.mywebsite.admin.service.AdminAiRuntimeConfigService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * agent-services 运行配置（Issue #146 UoW-1）。读挂 ai-model:read、写挂 ai-model:update
 * （不沿用模型入口控制器把写也挂 read 的旧惯例）；响应永不含 DSN/key 明文。
 */
@RestController
@RequestMapping("/api/admin/ai/runtime-config")
@Tag(name = "Admin · AI Runtime Config")
@SecurityRequirement(name = "adminBearer")
public class AdminAiRuntimeConfigController {

    private final AdminAiRuntimeConfigService adminAiRuntimeConfigService;

    public AdminAiRuntimeConfigController(AdminAiRuntimeConfigService adminAiRuntimeConfigService) {
        this.adminAiRuntimeConfigService = adminAiRuntimeConfigService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "运行配置视图：DSN 只回脱敏形、Gemini key 只回 hint，附配置版本与最近推送状态")
    public ResponseEntity<HttpMessageBody<AdminAiRuntimeConfigDto>> get() {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAiRuntimeConfigService.getConfig()));
    }

    @PutMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:update')")
    @Operation(summary = "保存运行配置（密钥字段留空保留原值；config_version +1）并立即推送 agent-services 热替换")
    public ResponseEntity<HttpMessageBody<AdminAiRuntimeConfigDto>> update(
            @Valid @RequestBody AdminAiRuntimeConfigUpdateRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAiRuntimeConfigService.updateConfig(request)));
    }

    @PostMapping("/retry-push")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:update')")
    @Operation(summary = "重试推送：只重推当前已存配置，不改数据、不递增版本")
    public ResponseEntity<HttpMessageBody<AdminAiRuntimeConfigDto>> retryPush() {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAiRuntimeConfigService.retryPush()));
    }
}
