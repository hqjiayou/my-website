package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetCreateRequest;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetDto;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetUpdateRequest;
import com.mywebsite.admin.service.AdminAiChainPresetService;
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
 * 对话链预设 CRUD（Issue #210 UoW-7）。权限码复用模型入口口径
 * （{@code admin:ai-model:read}，与 AdminAiModelEntryController 的读写同码一致），
 * 不新增 RBAC 种子。被 agent 引用的预设删除会被服务层拦截并报明确错误。
 */
@RestController
@RequestMapping("/api/admin/ai/chain-presets")
@Tag(name = "Admin · AI Chain Presets")
@SecurityRequirement(name = "adminBearer")
public class AdminAiChainPresetController {

    private final AdminAiChainPresetService chainPresetService;

    public AdminAiChainPresetController(AdminAiChainPresetService chainPresetService) {
        this.chainPresetService = chainPresetService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "列出对话链预设")
    public ResponseEntity<HttpMessageBody<List<AdminAiChainPresetDto>>> list() {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, chainPresetService.listPresets()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "查看对话链预设")
    public ResponseEntity<HttpMessageBody<AdminAiChainPresetDto>> get(@PathVariable long id) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, chainPresetService.getPreset(id)));
    }

    @PostMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "新建对话链预设")
    public ResponseEntity<HttpMessageBody<AdminAiChainPresetDto>> create(
            @Valid @RequestBody AdminAiChainPresetCreateRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, chainPresetService.createPreset(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "更新对话链预设（名称与成员及顺序）")
    public ResponseEntity<HttpMessageBody<AdminAiChainPresetDto>> update(
            @PathVariable long id, @Valid @RequestBody AdminAiChainPresetUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, chainPresetService.updatePreset(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "软删对话链预设（被 agent 引用时拦截报错）")
    public ResponseEntity<HttpMessageBody<Void>> delete(@PathVariable long id) {
        chainPresetService.deletePreset(id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }
}
