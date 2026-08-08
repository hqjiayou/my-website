package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminContentDtos.AdminContentCreateRequest;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentDetailDto;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentListItemDto;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentListQuery;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentUpdateRequest;
import com.mywebsite.admin.service.AdminContentService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sites/{siteId}/contents")
@Tag(name = "Admin · Content")
@SecurityRequirement(name = "adminBearer")
public class AdminContentController {

    private final AdminContentService adminContentService;

    public AdminContentController(AdminContentService adminContentService) {
        this.adminContentService = adminContentService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:content:list')")
    @Operation(summary = "站点内容分页列表")
    public ResponseEntity<HttpMessageBody<PageResult<AdminContentListItemDto>>> list(
            @PathVariable long siteId,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        PageResult<AdminContentListItemDto> data = adminContentService.listContents(
                siteId, new AdminContentListQuery(pageNum, pageSize, type, status));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:content:list')")
    @Operation(summary = "站点内容详情")
    public ResponseEntity<HttpMessageBody<AdminContentDetailDto>> get(
            @PathVariable long siteId, @PathVariable("id") long id) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminContentService.getContent(siteId, id)));
    }

    @PostMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:content:create')")
    @Operation(summary = "创建站点内容")
    public ResponseEntity<HttpMessageBody<AdminContentDetailDto>> create(
            @PathVariable long siteId, @Valid @RequestBody AdminContentCreateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminContentService.createContent(siteId, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:content:update')")
    @Operation(summary = "更新站点内容")
    public ResponseEntity<HttpMessageBody<AdminContentDetailDto>> update(
            @PathVariable long siteId,
            @PathVariable("id") long id,
            @Valid @RequestBody AdminContentUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminContentService.updateContent(siteId, id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:content:status')")
    @Operation(summary = "变更站点内容状态")
    public ResponseEntity<HttpMessageBody<AdminContentDetailDto>> updateStatus(
            @PathVariable long siteId,
            @PathVariable("id") long id,
            @Valid @RequestBody AdminContentStatusUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminContentService.updateStatus(siteId, id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:content:delete')")
    @Operation(summary = "删除站点内容（软删除）")
    public ResponseEntity<HttpMessageBody<Void>> delete(
            @PathVariable long siteId, @PathVariable("id") long id) {
        adminContentService.deleteContent(siteId, id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }
}
