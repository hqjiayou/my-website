package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiConversationListItemDto;
import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiConversationListQuery;
import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiMessageDto;
import com.mywebsite.admin.service.AdminAiConversationService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import com.mywebsite.common.page.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/agents/{agentId}/ai/conversations")
@Tag(name = "Admin · AI Conversation")
@SecurityRequirement(name = "adminBearer")
public class AdminAiConversationController {

    private final AdminAiConversationService adminAiConversationService;

    public AdminAiConversationController(AdminAiConversationService adminAiConversationService) {
        this.adminAiConversationService = adminAiConversationService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-conversation:list')")
    @Operation(summary = "站点 AI 会话分页列表（只读）")
    public ResponseEntity<HttpMessageBody<PageResult<AdminAiConversationListItemDto>>> list(
            @PathVariable long agentId,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        PageResult<AdminAiConversationListItemDto> data = adminAiConversationService.listConversations(
                agentId, new AdminAiConversationListQuery(pageNum, pageSize));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }

    @GetMapping("/{conversationId}/messages")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-conversation:list')")
    @Operation(summary = "站点 AI 会话消息列表（只读）")
    public ResponseEntity<HttpMessageBody<List<AdminAiMessageDto>>> messages(
            @PathVariable long agentId, @PathVariable long conversationId) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiConversationService.listMessages(agentId, conversationId)));
    }
}
