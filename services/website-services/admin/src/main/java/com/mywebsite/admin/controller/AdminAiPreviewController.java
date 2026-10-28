package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewChatRequest;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewSkillDto;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiMcpReadyDto;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiRuntimeDto;
import com.mywebsite.admin.service.AdminAiPreviewService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin/sites/{agentId}/ai")
@Tag(name = "Admin · AI Preview")
@SecurityRequirement(name = "adminBearer")
public class AdminAiPreviewController {

    private final AdminAiPreviewService adminAiPreviewService;

    public AdminAiPreviewController(AdminAiPreviewService adminAiPreviewService) {
        this.adminAiPreviewService = adminAiPreviewService;
    }

    @GetMapping("/preview/skills")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "预览可选 skill 目录（agent-services 平台能力，只元数据）")
    public ResponseEntity<HttpMessageBody<List<AdminAiPreviewSkillDto>>> listSkills(@PathVariable long agentId) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAiPreviewService.listSkills(agentId)));
    }

    @PostMapping(value = "/preview/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "站点 AI 预览流式对话（不落库；Prompt 由服务端注入）")
    public SseEmitter previewStream(
            @PathVariable long agentId, @Valid @RequestBody AdminAiPreviewChatRequest request) {
        return adminAiPreviewService.previewStream(agentId, request);
    }

    @GetMapping("/runtime")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "Agent 运行时快照（Provider / Fallback / MCP / 预算）；不可达时 reachable=false")
    public ResponseEntity<HttpMessageBody<AdminAiRuntimeDto>> runtime(@PathVariable long agentId) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAiPreviewService.runtimeStatus(agentId)));
    }

    @GetMapping("/mcp-ready")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-prompt:read')")
    @Operation(summary = "MCP 深检（手动）；agent-services 不可达时 reachable=false，MCP 不通时 probe.ok=false")
    public ResponseEntity<HttpMessageBody<AdminAiMcpReadyDto>> mcpReady(@PathVariable long agentId) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminAiPreviewService.mcpReady(agentId)));
    }
}
