package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeDetailDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeListItemDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeListQuery;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeUpdateRequest;
import com.mywebsite.admin.service.AdminAiKnowledgeService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import com.mywebsite.common.page.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sites/{siteId}/ai/knowledge-documents")
@Tag(name = "Admin · AI Knowledge Base")
@SecurityRequirement(name = "adminBearer")
public class AdminAiKnowledgeController {

    private final AdminAiKnowledgeService adminAiKnowledgeService;

    public AdminAiKnowledgeController(AdminAiKnowledgeService adminAiKnowledgeService) {
        this.adminAiKnowledgeService = adminAiKnowledgeService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "AI 知识库文档分页列表")
    public ResponseEntity<HttpMessageBody<PageResult<AdminAiKnowledgeListItemDto>>> list(
            @PathVariable long siteId,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status) {
        PageResult<AdminAiKnowledgeListItemDto> data = adminAiKnowledgeService.listDocuments(
                siteId, new AdminAiKnowledgeListQuery(pageNum, pageSize, status));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "AI 知识库文档详情")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDetailDto>> get(
            @PathVariable long siteId, @PathVariable("id") long id) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.getDocument(siteId, id)));
    }

    @PostMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:create')")
    @Operation(summary = "创建 AI 知识库文档")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDetailDto>> create(
            @PathVariable long siteId, @Valid @RequestBody AdminAiKnowledgeCreateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.createDocument(siteId, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:update')")
    @Operation(summary = "更新 AI 知识库文档")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDetailDto>> update(
            @PathVariable long siteId,
            @PathVariable("id") long id,
            @Valid @RequestBody AdminAiKnowledgeUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.updateDocument(siteId, id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:delete')")
    @Operation(summary = "删除 AI 知识库文档（软删除，并移除派生索引）")
    public ResponseEntity<HttpMessageBody<Void>> delete(
            @PathVariable long siteId, @PathVariable("id") long id) {
        adminAiKnowledgeService.deleteDocument(siteId, id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }
}
