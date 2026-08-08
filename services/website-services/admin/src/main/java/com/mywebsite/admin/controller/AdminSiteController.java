package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteCreateRequest;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteDetailDto;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteListItemDto;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteListQuery;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteUpdateRequest;
import com.mywebsite.admin.service.AdminSiteService;
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
@RequestMapping("/api/admin/sites")
@Tag(name = "Admin · Sites")
@SecurityRequirement(name = "adminBearer")
public class AdminSiteController {

    private final AdminSiteService adminSiteService;

    public AdminSiteController(AdminSiteService adminSiteService) {
        this.adminSiteService = adminSiteService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:site:list')")
    @Operation(summary = "站点分页列表")
    public ResponseEntity<HttpMessageBody<PageResult<AdminSiteListItemDto>>> list(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String siteType) {
        PageResult<AdminSiteListItemDto> data =
                adminSiteService.listSites(new AdminSiteListQuery(pageNum, pageSize, status, siteType));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:site:detail')")
    @Operation(summary = "站点详情")
    public ResponseEntity<HttpMessageBody<AdminSiteDetailDto>> get(@PathVariable long id) {
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, adminSiteService.getSite(id)));
    }

    @PostMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:site:create')")
    @Operation(summary = "创建站点")
    public ResponseEntity<HttpMessageBody<AdminSiteDetailDto>> create(
            @Valid @RequestBody AdminSiteCreateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, adminSiteService.createSite(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:site:update')")
    @Operation(summary = "更新站点")
    public ResponseEntity<HttpMessageBody<AdminSiteDetailDto>> update(
            @PathVariable long id, @Valid @RequestBody AdminSiteUpdateRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminSiteService.updateSite(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:site:delete')")
    @Operation(summary = "删除站点（软删除）")
    public ResponseEntity<HttpMessageBody<Void>> delete(@PathVariable long id) {
        adminSiteService.deleteSite(id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:site:status')")
    @Operation(summary = "变更站点状态")
    public ResponseEntity<HttpMessageBody<AdminSiteDetailDto>> updateStatus(
            @PathVariable long id, @Valid @RequestBody AdminSiteStatusUpdateRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminSiteService.updateStatus(id, request)));
    }
}
