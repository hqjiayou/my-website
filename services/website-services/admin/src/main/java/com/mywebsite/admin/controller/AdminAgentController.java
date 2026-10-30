package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentCreateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentDetailDto;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentListItemDto;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentListQuery;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentUpdateRequest;
import com.mywebsite.admin.service.AdminAgentService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import com.mywebsite.common.page.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/agents")
@Tag(name = "Admin · Sites")
@SecurityRequirement(name = "adminBearer")
public class AdminAgentController {

    private final AdminAgentService adminAgentService;

    public AdminAgentController(AdminAgentService adminAgentService) {
        this.adminAgentService = adminAgentService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:agent:list')")
    @Operation(summary = "站点分页列表")
    public ResponseEntity<HttpMessageBody<PageResult<AdminAgentListItemDto>>> list(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String agentType) {
        PageResult<AdminAgentListItemDto> data =
                adminAgentService.listSites(new AdminAgentListQuery(pageNum, pageSize, status, agentType));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:agent:list')")
    @Operation(summary = "站点详情")
    public ResponseEntity<HttpMessageBody<AdminAgentDetailDto>> get(@PathVariable long id) {
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAgentService.getSite(id)));
    }

    @PostMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:agent:list')")
    @Operation(summary = "创建站点")
    public ResponseEntity<HttpMessageBody<AdminAgentDetailDto>> create(
            @Valid @RequestBody AdminAgentCreateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAgentService.createSite(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:agent:list')")
    @Operation(summary = "更新站点")
    public ResponseEntity<HttpMessageBody<AdminAgentDetailDto>> update(
            @PathVariable long id, @Valid @RequestBody AdminAgentUpdateRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAgentService.updateSite(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:agent:list')")
    @Operation(summary = "删除站点（软删除）")
    public ResponseEntity<HttpMessageBody<Void>> delete(@PathVariable long id) {
        adminAgentService.deleteSite(id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:agent:list')")
    @Operation(summary = "变更站点状态")
    public ResponseEntity<HttpMessageBody<AdminAgentDetailDto>> updateStatus(
            @PathVariable long id, @Valid @RequestBody AdminAgentStatusUpdateRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAgentService.updateStatus(id, request)));
    }
}
