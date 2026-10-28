package com.mywebsite.site.controller;

import com.mywebsite.common.api.ApiResponse;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.AuthenticatedUser;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.site.dto.SiteAiChatRequestDto;
import com.mywebsite.site.dto.SiteAiChatResponseDto;
import com.mywebsite.site.dto.SiteAiConversationDto;
import com.mywebsite.site.dto.SiteAiConversationRenameRequestDto;
import com.mywebsite.site.dto.SiteAiMessageDto;
import com.mywebsite.site.dto.SiteKnowledgeSearchRequestDto;
import com.mywebsite.site.dto.SiteKnowledgeSearchResponseDto;
import com.mywebsite.site.dto.SiteUserMemoryDto;
import com.mywebsite.site.service.SiteAiConversationService;
import com.mywebsite.site.service.SiteAiService;
import com.mywebsite.site.service.SiteUserMemoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/site")
public class SiteAiController {

    private final SiteAiService siteAiService;
    private final SiteAiConversationService conversationService;
    private final SiteUserMemoryService memoryService;

    public SiteAiController(
            SiteAiService siteAiService,
            SiteAiConversationService conversationService,
            SiteUserMemoryService memoryService) {
        this.siteAiService = siteAiService;
        this.conversationService = conversationService;
        this.memoryService = memoryService;
    }

    @PostMapping("/{siteId}/ai/chat")
    public ApiResponse<SiteAiChatResponseDto> chat(
            @PathVariable("siteId") long siteId, @RequestBody SiteAiChatRequestDto request) {
        return ApiResponse.success(siteAiService.chat(siteId, request));
    }

    @PostMapping(value = "/{siteId}/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @PathVariable("siteId") long siteId, @RequestBody SiteAiChatRequestDto request) {
        return siteAiService.chatStream(siteId, request);
    }

    @PostMapping("/{siteId}/ai/knowledge/search")
    public ApiResponse<SiteKnowledgeSearchResponseDto> searchKnowledge(
            @PathVariable("siteId") long siteId, @Valid @RequestBody SiteKnowledgeSearchRequestDto request) {
        return ApiResponse.success(siteAiService.searchKnowledge(siteId, request));
    }

    @GetMapping("/{siteId}/ai/knowledge-documents")
    public ApiResponse<List<com.mywebsite.site.dto.SiteKnowledgeDocumentDtos.ListItem>> listKnowledgeDocuments(
            @PathVariable("siteId") long siteId) {
        return ApiResponse.success(siteAiService.listKnowledgeDocuments(siteId));
    }

    @GetMapping("/{siteId}/ai/knowledge-documents/{documentId}")
    public ApiResponse<com.mywebsite.site.dto.SiteKnowledgeDocumentDtos.Detail> getKnowledgeDocument(
            @PathVariable("siteId") long siteId, @PathVariable("documentId") long documentId) {
        return ApiResponse.success(siteAiService.getKnowledgeDocument(siteId, documentId));
    }

    @GetMapping("/{siteId}/ai/conversations")
    public ApiResponse<List<SiteAiConversationDto>> listConversations(@PathVariable("siteId") long siteId) {
        return ApiResponse.success(
                conversationService.listConversations(siteId, requireSiteUserId(siteId)));
    }

    @GetMapping("/{siteId}/ai/conversations/{conversationId}/messages")
    public ApiResponse<List<SiteAiMessageDto>> listMessages(
            @PathVariable("siteId") long siteId, @PathVariable("conversationId") long conversationId) {
        return ApiResponse.success(
                conversationService.listMessages(siteId, requireSiteUserId(siteId), conversationId));
    }

    @PatchMapping("/{siteId}/ai/conversations/{conversationId}")
    public ApiResponse<SiteAiConversationDto> renameConversation(
            @PathVariable("siteId") long siteId,
            @PathVariable("conversationId") long conversationId,
            @Valid @RequestBody SiteAiConversationRenameRequestDto request) {
        return ApiResponse.success(conversationService.renameConversation(
                siteId, requireSiteUserId(siteId), conversationId, request.title()));
    }

    @DeleteMapping("/{siteId}/ai/conversations/{conversationId}")
    public ApiResponse<Void> deleteConversation(
            @PathVariable("siteId") long siteId, @PathVariable("conversationId") long conversationId) {
        conversationService.deleteConversation(siteId, requireSiteUserId(siteId), conversationId);
        return ApiResponse.success();
    }

    @DeleteMapping("/{siteId}/ai/conversations/{conversationId}/messages/{messageId}")
    public ApiResponse<Void> truncateMessages(
            @PathVariable("siteId") long siteId,
            @PathVariable("conversationId") long conversationId,
            @PathVariable("messageId") long messageId) {
        conversationService.truncateMessages(siteId, requireSiteUserId(siteId), conversationId, messageId);
        return ApiResponse.success();
    }

    @GetMapping("/{siteId}/ai/memories")
    public ApiResponse<List<SiteUserMemoryDto>> listMemories(@PathVariable("siteId") long siteId) {
        long siteUserId = requireSiteUserId(siteId);
        return ApiResponse.success(memoryService.listOwn(siteId, siteUserId));
    }

    @DeleteMapping("/{siteId}/ai/memories/{memoryId}")
    public ApiResponse<Void> deleteMemory(
            @PathVariable("siteId") long siteId, @PathVariable("memoryId") long memoryId) {
        memoryService.deleteOwn(siteId, requireSiteUserId(siteId), memoryId);
        return ApiResponse.success();
    }

    @DeleteMapping("/{siteId}/ai/memories")
    public ApiResponse<Void> clearMemories(@PathVariable("siteId") long siteId) {
        memoryService.clearOwn(siteId, requireSiteUserId(siteId));
        return ApiResponse.success();
    }

    private static long requireSiteUserId(long siteId) {
        AuthenticatedUser user =
                AuthContext.currentUser().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.siteIdOrNull() == null || !Objects.equals(user.siteIdOrNull(), siteId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return SubjectCodes.parseSiteUserId(user.subject())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
