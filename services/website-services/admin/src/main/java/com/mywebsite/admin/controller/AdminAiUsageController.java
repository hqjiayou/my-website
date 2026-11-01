package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiUsageDtos.AdminAiUsageReportDto;
import com.mywebsite.admin.service.AdminAiUsageService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AI 用量记账只读聚合（Issue #140 UoW-2）。权限沿用模型入口读权限，不新增 ACTION。 */
@RestController
@RequestMapping("/api/admin/ai/usage")
@Tag(name = "Admin · AI Usage")
@SecurityRequirement(name = "adminBearer")
public class AdminAiUsageController {

    private final AdminAiUsageService adminAiUsageService;

    public AdminAiUsageController(AdminAiUsageService adminAiUsageService) {
        this.adminAiUsageService = adminAiUsageService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "AI 用量聚合：按日/agent/模型聚合 tokens 与次数，近期明细按时间倒序，可按 source 筛选")
    public ResponseEntity<HttpMessageBody<AdminAiUsageReportDto>> usage(
            @RequestParam(required = false) String source,
            @RequestParam(required = false, defaultValue = "14") int days,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAiUsageService.usageReport(source, days, limit)));
    }
}
