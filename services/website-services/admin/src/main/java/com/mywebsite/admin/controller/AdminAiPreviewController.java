package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewChatRequest;
import com.mywebsite.admin.service.AdminAiPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin/sites/{siteId}/ai/preview")
@Tag(name = "Admin · AI Preview")
@SecurityRequirement(name = "adminBearer")
public class AdminAiPreviewController {

    private final AdminAiPreviewService adminAiPreviewService;

    public AdminAiPreviewController(AdminAiPreviewService adminAiPreviewService) {
        this.adminAiPreviewService = adminAiPreviewService;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "站点 AI 预览流式对话（不落库；Prompt 由服务端注入）")
    public SseEmitter previewStream(
            @PathVariable long siteId, @Valid @RequestBody AdminAiPreviewChatRequest request) {
        return adminAiPreviewService.previewStream(siteId, request);
    }
}
