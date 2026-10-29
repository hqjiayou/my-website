package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesApiKeyCreatedDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesApiKeyWriteRequest;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesApiKeysDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesLoginDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesLoginRequest;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesLoginStatusDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesStatusDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesUsageDto;
import com.mywebsite.admin.service.AdminProxyServicesService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai/proxy-services")
@Tag(name = "Admin · proxy-services")
@SecurityRequirement(name = "adminBearer")
public class AdminProxyServicesController {

    private final AdminProxyServicesService adminProxyServicesService;

    public AdminProxyServicesController(AdminProxyServicesService adminProxyServicesService) {
        this.adminProxyServicesService = adminProxyServicesService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "proxy-services 进程状态与已登录账号（不含密钥）")
    public ResponseEntity<HttpMessageBody<AdminProxyServicesStatusDto>> status() {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminProxyServicesService.status()));
    }

    @GetMapping("/usage")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "对外调用地址，以及已登录账号放出的模型名")
    public ResponseEntity<HttpMessageBody<AdminProxyServicesUsageDto>> usage() {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminProxyServicesService.usage()));
    }

    @GetMapping("/api-keys")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "客户端 API key 列表（调用代理用，不是上游模型密钥）")
    public ResponseEntity<HttpMessageBody<AdminProxyServicesApiKeysDto>> apiKeys() {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminProxyServicesService.listApiKeys()));
    }

    @PostMapping("/api-keys")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:update')")
    @Operation(summary = "新增客户端 API key；apiKey 为空时由服务端生成")
    public ResponseEntity<HttpMessageBody<AdminProxyServicesApiKeyCreatedDto>> createApiKey(
            @RequestBody(required = false) AdminProxyServicesApiKeyWriteRequest request) {
        String apiKey = request == null ? null : request.apiKey();
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, new AdminProxyServicesApiKeyCreatedDto(adminProxyServicesService.createApiKey(apiKey))));
    }

    @DeleteMapping("/api-keys")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:update')")
    @Operation(summary = "删除一把客户端 API key")
    public ResponseEntity<HttpMessageBody<Void>> deleteApiKey(@RequestBody AdminProxyServicesApiKeyWriteRequest request) {
        adminProxyServicesService.deleteApiKey(request == null ? null : request.apiKey());
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }

    @PostMapping("/logins")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:update')")
    @Operation(summary = "发起 GPT CLI（codex）或 Claude 登录，返回授权链接")
    public ResponseEntity<HttpMessageBody<AdminProxyServicesLoginDto>> startLogin(
            @RequestBody(required = false) AdminProxyServicesLoginRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS,
                adminProxyServicesService.startLogin(request == null ? null : request.provider())));
    }

    @GetMapping("/logins/status")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:update')")
    @Operation(summary = "查询一次登录会话：wait、ok 或 error")
    public ResponseEntity<HttpMessageBody<AdminProxyServicesLoginStatusDto>> loginStatus(@RequestParam String state) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminProxyServicesService.loginStatus(state)));
    }
}
