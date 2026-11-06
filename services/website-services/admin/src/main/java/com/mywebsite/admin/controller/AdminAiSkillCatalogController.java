package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiSkillCatalogDtos.AdminAiSkillCatalogDto;
import com.mywebsite.admin.service.AdminAiSkillCatalogService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skills 货架目录（Issue #214 UoW-6）：工具组只读页的数据面。目录是平台
 * 能力、不挂在某个 agent 之下；权限沿 {@code admin:ai-model:read} 既有
 * 口径（与 MCP 注册表页同），不新增权限码。技能本体是 agent-services
 * 的文件定义，本端点只回元数据与自选开关状态，无写操作。
 */
@RestController
@RequestMapping("/api/admin/ai/skills")
@Tag(name = "Admin · AI Skills")
@SecurityRequirement(name = "adminBearer")
public class AdminAiSkillCatalogController {

    private final AdminAiSkillCatalogService skillCatalogService;

    public AdminAiSkillCatalogController(AdminAiSkillCatalogService skillCatalogService) {
        this.skillCatalogService = skillCatalogService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "技能目录全量（平台级，只元数据）＋自选开关状态")
    public ResponseEntity<HttpMessageBody<AdminAiSkillCatalogDto>> getCatalog() {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, skillCatalogService.getCatalog()));
    }
}
