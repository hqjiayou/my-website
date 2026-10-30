package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminMenuNodeDto;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminMenuCreateRequest;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminMenuUpdateRequest;
import com.mywebsite.admin.service.AdminMenuService;
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
@RequestMapping("/api/admin/menus")
@Tag(name = "Admin · Menus")
@SecurityRequirement(name = "adminBearer")
public class AdminMenuController {

    private final AdminMenuService adminMenuService;

    public AdminMenuController(AdminMenuService adminMenuService) {
        this.adminMenuService = adminMenuService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:menu:list')")
    @Operation(summary = "菜单树（含停用，不含已删）")
    public ResponseEntity<HttpMessageBody<List<AdminMenuNodeDto>>> list() {
        List<AdminMenuNodeDto> data = adminMenuService.listManageTree();
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }

    @PostMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:menu:list')")
    @Operation(summary = "新增菜单")
    public ResponseEntity<HttpMessageBody<AdminMenuNodeDto>> create(@Valid @RequestBody AdminMenuCreateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, adminMenuService.createMenu(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:menu:list')")
    @Operation(summary = "修改菜单")
    public ResponseEntity<HttpMessageBody<AdminMenuNodeDto>> update(
            @PathVariable long id, @Valid @RequestBody AdminMenuUpdateRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminMenuService.updateMenu(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:menu:list')")
    @Operation(summary = "删除菜单（软删除，有子菜单时拒绝）")
    public ResponseEntity<HttpMessageBody<Void>> delete(@PathVariable long id) {
        adminMenuService.deleteMenu(id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }
}
