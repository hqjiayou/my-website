package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminMenuNodeDto;
import com.mywebsite.admin.service.AdminMenuService;
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
    @Operation(summary = "菜单树（只读，全量 ACTIVE MENU）")
    public ResponseEntity<HttpMessageBody<List<AdminMenuNodeDto>>> list() {
        List<AdminMenuNodeDto> data = adminMenuService.listActiveMenuTree();
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }
}
