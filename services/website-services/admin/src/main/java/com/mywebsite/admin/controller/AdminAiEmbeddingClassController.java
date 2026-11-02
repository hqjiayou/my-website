package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminEmbeddingClassDtos.AdminEmbeddingClassDto;
import com.mywebsite.admin.dto.AdminEmbeddingClassDtos.AdminEmbeddingClassSaveRequest;
import com.mywebsite.admin.service.AdminEmbeddingRegistrationService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 嵌入类登记（Issue #126 UoW-1）。读写均挂 ai-model:read——与模型入口写口的
 * 现行执法口径一致（kou 2026-11-03 拍板，区别于运行配置的 update）。
 * 登记行只引用模型入口，响应不含任何 key。
 */
@RestController
@RequestMapping("/api/admin/ai/embedding-classes")
@Tag(name = "Admin · AI Embedding Classes")
@SecurityRequirement(name = "adminBearer")
public class AdminAiEmbeddingClassController {

    private final AdminEmbeddingRegistrationService registrationService;

    public AdminAiEmbeddingClassController(AdminEmbeddingRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "某 scope 的嵌入类登记（agentId 缺省＝全站 scope），附从入口解析的 spec 与类键")
    public ResponseEntity<HttpMessageBody<List<AdminEmbeddingClassDto>>> list(
            @RequestParam(name = "agentId", required = false) Long agentId) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, registrationService.listRegistrations(agentId)));
    }

    @PutMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-model:read')")
    @Operation(summary = "整表保存嵌入类登记：有序 entry 清单整体替换该 scope（数组序＝优先级，上限 4 类）")
    public ResponseEntity<HttpMessageBody<List<AdminEmbeddingClassDto>>> replace(
            @RequestParam(name = "agentId", required = false) Long agentId,
            @Valid @RequestBody AdminEmbeddingClassSaveRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, registrationService.replaceRegistrations(agentId, request)));
    }
}
