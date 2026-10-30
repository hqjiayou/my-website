package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminGatewayAccessKeyDtos.AdminGatewayAccessKeyDto;
import com.mywebsite.admin.dto.AdminGatewayAccessKeyDtos.AdminGatewayAccessKeyIssueRequest;
import com.mywebsite.admin.dto.AdminGatewayAccessKeyDtos.AdminGatewayAccessKeyIssueResponse;
import com.mywebsite.admin.service.AdminGatewayAccessKeyService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai/gateway-access-keys")
@Tag(name = "Admin · Gateway Access Key")
@SecurityRequirement(name = "adminBearer")
public class AdminGatewayAccessKeyController {

    private final AdminGatewayAccessKeyService adminGatewayAccessKeyService;

    public AdminGatewayAccessKeyController(AdminGatewayAccessKeyService adminGatewayAccessKeyService) {
        this.adminGatewayAccessKeyService = adminGatewayAccessKeyService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "网关访问 Key 列表（不含明文与哈希）")
    public ResponseEntity<HttpMessageBody<List<AdminGatewayAccessKeyDto>>> list() {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminGatewayAccessKeyService.list()));
    }

    @PostMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "签发网关访问 Key；明文只在本次响应中返回一次")
    public ResponseEntity<HttpMessageBody<AdminGatewayAccessKeyIssueResponse>> issue(
            @Valid @RequestBody AdminGatewayAccessKeyIssueRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminGatewayAccessKeyService.issue(request)));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "吊销网关访问 Key；已吊销重复调用幂等")
    public ResponseEntity<HttpMessageBody<AdminGatewayAccessKeyDto>> revoke(@PathVariable long id) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminGatewayAccessKeyService.revoke(id)));
    }
}
