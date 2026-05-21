package com.mywebsite.admin.rbac.controller;

import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminPermissionItemDto;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminPermissionListQuery;
import com.mywebsite.admin.rbac.service.AdminPermissionManagementService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/permissions")
@Tag(name = "Admin · Permissions")
@SecurityRequirement(name = "adminBearer")
public class AdminPermissionController {

    private final AdminPermissionManagementService permissionManagementService;

    public AdminPermissionController(AdminPermissionManagementService permissionManagementService) {
        this.permissionManagementService = permissionManagementService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:permission:list')")
    @Operation(summary = "权限点列表（只读）")
    public ResponseEntity<HttpMessageBody<List<AdminPermissionItemDto>>> list(
            @RequestParam(required = false) String permissionCode,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceKey) {
        List<AdminPermissionItemDto> data = permissionManagementService.listPermissions(
                new AdminPermissionListQuery(permissionCode, resourceType, resourceKey));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }
}
