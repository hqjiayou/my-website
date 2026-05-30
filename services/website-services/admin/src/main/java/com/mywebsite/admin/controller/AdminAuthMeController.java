package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAuthMeDto;
import com.mywebsite.admin.service.AdminAuthMeService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "Admin · Auth Context")
public class AdminAuthMeController {

    private final AdminAuthMeService adminAuthMeService;

    public AdminAuthMeController(AdminAuthMeService adminAuthMeService) {
        this.adminAuthMeService = adminAuthMeService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "当前管理员上下文（用户 / 角色 / 权限 / 菜单）")
    public ResponseEntity<HttpMessageBody<AdminAuthMeDto>> me() {
        AdminAuthMeDto data = adminAuthMeService.loadCurrentAdminContext();
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }
}
