package com.mywebsite.agent.controller;

import com.mywebsite.common.api.ApiResponse;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.AuthenticatedUser;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.agent.dto.AgentAiChatRequestDto;
import com.mywebsite.agent.dto.AgentAiChatResponseDto;
import com.mywebsite.agent.dto.AgentAiConversationDto;
import com.mywebsite.agent.dto.AgentAiConversationRenameRequestDto;
import com.mywebsite.agent.dto.AgentAiMessageDto;
import com.mywebsite.agent.dto.AgentAiSearchHitDto;
import com.mywebsite.agent.dto.AgentAiShareDto;
import com.mywebsite.agent.dto.AgentAiSharedConversationDto;
import com.mywebsite.agent.dto.AgentKnowledgeSearchRequestDto;
import com.mywebsite.agent.dto.AgentKnowledgeSearchResponseDto;
import com.mywebsite.agent.dto.AgentUserMemoryDto;
import com.mywebsite.agent.service.AgentAiConversationService;
import com.mywebsite.agent.service.AgentAiService;
import com.mywebsite.agent.service.AgentUserMemoryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent")
public class AgentAiController {

    private final AgentAiService agentAiService;
    private final AgentAiConversationService conversationService;
    private final AgentUserMemoryService memoryService;

    public AgentAiController(
            AgentAiService agentAiService,
            AgentAiConversationService conversationService,
            AgentUserMemoryService memoryService) {
        this.agentAiService = agentAiService;
        this.conversationService = conversationService;
        this.memoryService = memoryService;
    }

    @PostMapping("/{agentId}/ai/chat")
    public ApiResponse<AgentAiChatResponseDto> chat(
            @PathVariable("agentId") long agentId, @RequestBody AgentAiChatRequestDto request) {
        return ApiResponse.success(agentAiService.chat(agentId, request));
    }

    @PostMapping(value = "/{agentId}/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @PathVariable("agentId") long agentId, @RequestBody AgentAiChatRequestDto request) {
        return agentAiService.chatStream(agentId, request);
    }

    @PostMapping("/{agentId}/ai/knowledge/search")
    public ApiResponse<AgentKnowledgeSearchResponseDto> searchKnowledge(
            @PathVariable("agentId") long agentId, @Valid @RequestBody AgentKnowledgeSearchRequestDto request) {
        return ApiResponse.success(agentAiService.searchKnowledge(agentId, request));
    }

    @GetMapping("/{agentId}/ai/knowledge-documents")
    public ApiResponse<List<com.mywebsite.agent.dto.AgentKnowledgeDocumentDtos.ListItem>> listKnowledgeDocuments(
            @PathVariable("agentId") long agentId) {
        return ApiResponse.success(agentAiService.listKnowledgeDocuments(agentId));
    }

    @GetMapping("/{agentId}/ai/knowledge-documents/{documentId}")
    public ApiResponse<com.mywebsite.agent.dto.AgentKnowledgeDocumentDtos.Detail> getKnowledgeDocument(
            @PathVariable("agentId") long agentId, @PathVariable("documentId") long documentId) {
        return ApiResponse.success(agentAiService.getKnowledgeDocument(agentId, documentId));
    }

    @GetMapping("/{agentId}/ai/conversations")
    public ApiResponse<List<AgentAiConversationDto>> listConversations(@PathVariable("agentId") long agentId) {
        return ApiResponse.success(
                conversationService.listConversations(agentId, requireAgentUserId(agentId)));
    }

    @GetMapping("/{agentId}/ai/conversations/{conversationId}/messages")
    public ApiResponse<List<AgentAiMessageDto>> listMessages(
            @PathVariable("agentId") long agentId, @PathVariable("conversationId") long conversationId) {
        return ApiResponse.success(
                conversationService.listMessages(agentId, requireAgentUserId(agentId), conversationId));
    }

    @PatchMapping("/{agentId}/ai/conversations/{conversationId}")
    public ApiResponse<AgentAiConversationDto> renameConversation(
            @PathVariable("agentId") long agentId,
            @PathVariable("conversationId") long conversationId,
            @Valid @RequestBody AgentAiConversationRenameRequestDto request) {
        return ApiResponse.success(conversationService.renameConversation(
                agentId, requireAgentUserId(agentId), conversationId, request.title()));
    }

    @DeleteMapping("/{agentId}/ai/conversations/{conversationId}")
    public ApiResponse<Void> deleteConversation(
            @PathVariable("agentId") long agentId, @PathVariable("conversationId") long conversationId) {
        conversationService.deleteConversation(agentId, requireAgentUserId(agentId), conversationId);
        return ApiResponse.success();
    }

    @DeleteMapping("/{agentId}/ai/conversations/{conversationId}/messages/{messageId}")
    public ApiResponse<Void> truncateMessages(
            @PathVariable("agentId") long agentId,
            @PathVariable("conversationId") long conversationId,
            @PathVariable("messageId") long messageId) {
        conversationService.truncateMessages(agentId, requireAgentUserId(agentId), conversationId, messageId);
        return ApiResponse.success();
    }

    @PostMapping("/{agentId}/ai/conversations/{conversationId}/share")
    public ApiResponse<AgentAiShareDto> createOrRefreshShare(
            @PathVariable("agentId") long agentId, @PathVariable("conversationId") long conversationId) {
        return ApiResponse.success(
                conversationService.createOrRefreshShare(agentId, requireAgentUserId(agentId), conversationId));
    }

    @GetMapping("/{agentId}/ai/conversations/{conversationId}/share")
    public ApiResponse<AgentAiShareDto> getShare(
            @PathVariable("agentId") long agentId, @PathVariable("conversationId") long conversationId) {
        return ApiResponse.success(
                conversationService.getShare(agentId, requireAgentUserId(agentId), conversationId));
    }

    @DeleteMapping("/{agentId}/ai/conversations/{conversationId}/share")
    public ApiResponse<Void> revokeShare(
            @PathVariable("agentId") long agentId, @PathVariable("conversationId") long conversationId) {
        conversationService.revokeShare(agentId, requireAgentUserId(agentId), conversationId);
        return ApiResponse.success();
    }

    @GetMapping("/{agentId}/ai/search")
    public ApiResponse<List<AgentAiSearchHitDto>> searchConversations(
            @PathVariable("agentId") long agentId, @RequestParam("q") String query) {
        return ApiResponse.success(
                conversationService.searchConversations(agentId, requireAgentUserId(agentId), query));
    }

    /** 公開分享讀：/api/agent/public/** 在 AgentSecurityConfig 已 permitAll（僅 GET），只憑 token，不走站點登入。 */
    @GetMapping("/public/ai/shares/{token}")
    public ApiResponse<AgentAiSharedConversationDto> getSharedConversation(@PathVariable("token") String token) {
        return ApiResponse.success(conversationService.getSharedConversation(token));
    }

    @GetMapping("/{agentId}/ai/memories")
    public ApiResponse<List<AgentUserMemoryDto>> listMemories(@PathVariable("agentId") long agentId) {
        long agentUserId = requireAgentUserId(agentId);
        return ApiResponse.success(memoryService.listOwn(agentId, agentUserId));
    }

    @DeleteMapping("/{agentId}/ai/memories/{memoryId}")
    public ApiResponse<Void> deleteMemory(
            @PathVariable("agentId") long agentId, @PathVariable("memoryId") long memoryId) {
        memoryService.deleteOwn(agentId, requireAgentUserId(agentId), memoryId);
        return ApiResponse.success();
    }

    @DeleteMapping("/{agentId}/ai/memories")
    public ApiResponse<Void> clearMemories(@PathVariable("agentId") long agentId) {
        memoryService.clearOwn(agentId, requireAgentUserId(agentId));
        return ApiResponse.success();
    }

    private static long requireAgentUserId(long agentId) {
        AuthenticatedUser user =
                AuthContext.currentUser().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.agentIdOrNull() == null || !Objects.equals(user.agentIdOrNull(), agentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return SubjectCodes.parseAgentUserId(user.subject())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
