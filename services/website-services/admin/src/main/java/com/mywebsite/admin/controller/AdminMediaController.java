package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaDetailDto;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaListItemDto;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaListQuery;
import com.mywebsite.admin.dto.AdminMediaDtos.AdminMediaPreviewDto;
import com.mywebsite.admin.service.AdminMediaService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import com.mywebsite.common.page.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/sites/{siteId}/media")
@Tag(name = "Admin · Media")
@SecurityRequirement(name = "adminBearer")
public class AdminMediaController {

    private final AdminMediaService adminMediaService;

    public AdminMediaController(AdminMediaService adminMediaService) {
        this.adminMediaService = adminMediaService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:media:list')")
    @Operation(summary = "站点媒体分页列表")
    public ResponseEntity<HttpMessageBody<PageResult<AdminMediaListItemDto>>> list(
            @PathVariable long siteId,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String contentTypePrefix) {
        PageResult<AdminMediaListItemDto> data =
                adminMediaService.listMedia(siteId, new AdminMediaListQuery(pageNum, pageSize, contentTypePrefix));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:media:upload')")
    @Operation(summary = "上传站点媒体")
    public ResponseEntity<HttpMessageBody<AdminMediaDetailDto>> upload(
            @PathVariable long siteId, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminMediaService.upload(siteId, file)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:media:list')")
    @Operation(summary = "站点媒体详情")
    public ResponseEntity<HttpMessageBody<AdminMediaDetailDto>> get(
            @PathVariable long siteId, @PathVariable("id") long id) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminMediaService.getMedia(siteId, id)));
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:media:download')")
    @Operation(summary = "站点媒体预览（短时 presigned URL）")
    public ResponseEntity<HttpMessageBody<AdminMediaPreviewDto>> preview(
            @PathVariable long siteId, @PathVariable("id") long id) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, adminMediaService.preview(siteId, id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:media:delete')")
    @Operation(summary = "删除站点媒体（软删 + 触发对象清理）")
    public ResponseEntity<HttpMessageBody<Void>> delete(
            @PathVariable long siteId, @PathVariable("id") long id) {
        adminMediaService.deleteMedia(siteId, id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }
}
