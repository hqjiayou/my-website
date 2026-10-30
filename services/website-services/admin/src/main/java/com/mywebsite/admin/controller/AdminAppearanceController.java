package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAppearanceDtos.AdminAppearanceDetailDto;
import com.mywebsite.admin.dto.AdminAppearanceDtos.AdminAppearancePutRequest;
import com.mywebsite.admin.service.AdminAppearanceService;
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
@RequestMapping("/api/admin/agents/{id}/appearance")
@Tag(name = "Admin · Agent Appearance")
@SecurityRequirement(name = "adminBearer")
public class AdminAppearanceController {

    private final AdminAppearanceService adminAppearanceService;

    public AdminAppearanceController(AdminAppearanceService adminAppearanceService) {
        this.adminAppearanceService = adminAppearanceService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:agent:list')")
    @Operation(summary = "站点外观详情")
    public ResponseEntity<HttpMessageBody<AdminAppearanceDetailDto>> get(@PathVariable("id") long agentId) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAppearanceService.getAppearance(agentId)));
    }

    @PutMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:agent:list')")
    @Operation(summary = "整份替换站点外观")
    public ResponseEntity<HttpMessageBody<AdminAppearanceDetailDto>> put(
            @PathVariable("id") long agentId, @Valid @RequestBody AdminAppearancePutRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAppearanceService.putAppearance(agentId, request)));
    }
}
