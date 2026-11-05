package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiConfigPresetDtos.AdminAiConfigPresetCreateRequest;
import com.mywebsite.admin.dto.AdminAiConfigPresetDtos.AdminAiConfigPresetDto;
import com.mywebsite.admin.dto.AdminAiConfigPresetDtos.AdminAiConfigPresetUpdateRequest;
import com.mywebsite.admin.service.AdminAiConfigPresetService;
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
 * AI 配置预设 CRUD（Issue #211 UoW-8）。权限码复用 ai-prompt 现有口径
 * （{@code admin:ai-prompt:read}，与 AdminAiPromptController 的读写同码一致），
 * 不新增 RBAC 种子；页面随 #213 A1 落，本单只交后端。
 */
@RestController
@RequestMapping("/api/admin/ai/config-presets")
@Tag(name = "Admin · AI Config Presets")
@SecurityRequirement(name = "adminBearer")
public class AdminAiConfigPresetController {

    private final AdminAiConfigPresetService configPresetService;

    public AdminAiConfigPresetController(AdminAiConfigPresetService configPresetService) {
        this.configPresetService = configPresetService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "列出 AI 配置预设")
    public ResponseEntity<HttpMessageBody<List<AdminAiConfigPresetDto>>> list() {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, configPresetService.listPresets()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "查看 AI 配置预设")
    public ResponseEntity<HttpMessageBody<AdminAiConfigPresetDto>> get(@PathVariable long id) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, configPresetService.getPreset(id)));
    }

    @PostMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "新建 AI 配置预设")
    public ResponseEntity<HttpMessageBody<AdminAiConfigPresetDto>> create(
            @Valid @RequestBody AdminAiConfigPresetCreateRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, configPresetService.createPreset(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "更新 AI 配置预设")
    public ResponseEntity<HttpMessageBody<AdminAiConfigPresetDto>> update(
            @PathVariable long id, @Valid @RequestBody AdminAiConfigPresetUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, configPresetService.updatePreset(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "软删 AI 配置预设（引用它的 agent 解析时回退为无配置）")
    public ResponseEntity<HttpMessageBody<Void>> delete(@PathVariable long id) {
        configPresetService.softDeletePreset(id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }
}
