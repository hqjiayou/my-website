package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiCliEntrySyncReportDto;
import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryDto;
import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryWriteRequest;
import com.mywebsite.admin.service.AdminAiCliEntrySyncService;
import com.mywebsite.admin.service.AdminAiModelEntryService;
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

@RestController
@RequestMapping("/api/admin/ai/model-entries")
@Tag(name = "Admin · AI Model Entry")
@SecurityRequirement(name = "adminBearer")
public class AdminAiModelEntryController {

    private final AdminAiModelEntryService adminAiModelEntryService;
    private final AdminAiCliEntrySyncService adminAiCliEntrySyncService;

    public AdminAiModelEntryController(
            AdminAiModelEntryService adminAiModelEntryService,
            AdminAiCliEntrySyncService adminAiCliEntrySyncService) {
        this.adminAiModelEntryService = adminAiModelEntryService;
        this.adminAiCliEntrySyncService = adminAiCliEntrySyncService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "模型入口列表（不含 API key 正文）")
    public ResponseEntity<HttpMessageBody<List<AdminAiModelEntryDto>>> list() {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAiModelEntryService.listEntries()));
    }

    @PostMapping("/sync-cli")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "按 CLI 代理当前账号/模型清单同步派生条目（Issue #214 UoW-2）")
    public ResponseEntity<HttpMessageBody<AdminAiCliEntrySyncReportDto>> syncCli() {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiCliEntrySyncService.syncFromProxy()));
    }

    @PostMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "新增模型入口")
    public ResponseEntity<HttpMessageBody<AdminAiModelEntryDto>> create(
            @Valid @RequestBody AdminAiModelEntryWriteRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAiModelEntryService.createEntry(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "更新模型入口；apiKey 留空则保留原密钥")
    public ResponseEntity<HttpMessageBody<AdminAiModelEntryDto>> update(
            @PathVariable long id, @Valid @RequestBody AdminAiModelEntryWriteRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiModelEntryService.updateEntry(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "删除模型入口")
    public ResponseEntity<HttpMessageBody<Void>> delete(@PathVariable long id) {
        adminAiModelEntryService.deleteEntry(id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }
}
