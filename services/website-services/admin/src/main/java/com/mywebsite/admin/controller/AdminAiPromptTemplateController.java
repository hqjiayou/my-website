package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAiPromptTemplateCreateRequest;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAiPromptTemplateDto;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAiPromptTemplateUpdateRequest;
import com.mywebsite.admin.service.AdminAiPromptTemplateService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 模板 CRUD（Issue #214 UoW-4）。权限码沿 ai-prompt 既有口径
 * （{@code admin:ai-prompt:read}），不新增 RBAC 种子；取代已退役的配置预设
 * 端点（{@code /api/admin/ai/config-presets}，#211）。
 */
@RestController
@RequestMapping("/api/admin/ai/prompt-templates")
@Tag(name = "Admin · AI Prompt Templates")
@SecurityRequirement(name = "adminBearer")
public class AdminAiPromptTemplateController {

    private final AdminAiPromptTemplateService promptTemplateService;

    public AdminAiPromptTemplateController(AdminAiPromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "列出 Prompt 模板")
    public ResponseEntity<HttpMessageBody<List<AdminAiPromptTemplateDto>>> list() {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, promptTemplateService.listTemplates()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "查看 Prompt 模板")
    public ResponseEntity<HttpMessageBody<AdminAiPromptTemplateDto>> get(@PathVariable long id) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, promptTemplateService.getTemplate(id)));
    }

    @PostMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "新建 Prompt 模板")
    public ResponseEntity<HttpMessageBody<AdminAiPromptTemplateDto>> create(
            @Valid @RequestBody AdminAiPromptTemplateCreateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, promptTemplateService.createTemplate(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "更新 Prompt 模板（不影响已套用的 agent 本地副本）")
    public ResponseEntity<HttpMessageBody<AdminAiPromptTemplateDto>> update(
            @PathVariable long id, @Valid @RequestBody AdminAiPromptTemplateUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, promptTemplateService.updateTemplate(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "软删 Prompt 模板（已套用 agent 的本地副本不受影响）")
    public ResponseEntity<HttpMessageBody<Void>> delete(@PathVariable long id) {
        promptTemplateService.softDeleteTemplate(id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }
}
