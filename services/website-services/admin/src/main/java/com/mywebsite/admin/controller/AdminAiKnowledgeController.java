package com.mywebsite.admin.controller;

import com.mywebsite.admin.constant.AdminAiKnowledgeMessageKeys;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeDetailDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeFileDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeListItemDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeListQuery;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeReembedConflictResponse;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeReembedResultDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeUpdateRequest;
import com.mywebsite.admin.service.AdminAiKnowledgeContentConverter;
import com.mywebsite.admin.service.AdminAiKnowledgeService;
import com.mywebsite.admin.service.AdminAiKnowledgeTextExtractor;
import com.mywebsite.admin.service.AdminAiKnowledgeService.ReembedConflictException;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Admin · AI Knowledge Base")
@SecurityRequirement(name = "adminBearer")
public class AdminAiKnowledgeController {

    private static final String DOCUMENTS = "/api/admin/sites/{siteId}/ai/knowledge-documents";
    private static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    /** 抽取文本上限（字符），与 agent-services 的 MAX_CONTENT_LENGTH 对齐。 */
    private static final int MAX_CONTENT_CHARS = 3_000_000;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "csv", "tsv", "json", "xml", "yaml", "yml", "toml", "ini", "log",
            "py", "java", "js", "ts", "tsx", "jsx", "css", "scss", "sql", "sh",
            "go", "rs", "c", "h", "cpp", "hpp", "cs", "rb", "php", "swift", "kt",
            "scala", "pl", "r", "lua", "vue",
            "pdf", "docx", "html", "htm", "xlsx", "pptx", "epub", "odt", "ods", "odp", "rtf",
            "png", "jpg", "jpeg", "webp", "gif", "bmp");

    /** 图片扩展名：不走文本转换器，内容来自视觉转写（见 toUploadPayload）。 */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif", "bmp");

    private static final java.util.Map<String, String> CONTENT_TYPES = java.util.Map.ofEntries(
            java.util.Map.entry("pdf", "application/pdf"),
            java.util.Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            java.util.Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            java.util.Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            java.util.Map.entry("html", "text/html"),
            java.util.Map.entry("htm", "text/html"),
            java.util.Map.entry("epub", "application/epub+zip"),
            java.util.Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            java.util.Map.entry("ods", "application/vnd.oasis.opendocument.spreadsheet"),
            java.util.Map.entry("odp", "application/vnd.oasis.opendocument.presentation"),
            java.util.Map.entry("rtf", "application/rtf"),
            java.util.Map.entry("json", "application/json"),
            java.util.Map.entry("csv", "text/csv"),
            java.util.Map.entry("md", "text/markdown"),
            java.util.Map.entry("png", "image/png"),
            java.util.Map.entry("jpg", "image/jpeg"),
            java.util.Map.entry("jpeg", "image/jpeg"),
            java.util.Map.entry("webp", "image/webp"),
            java.util.Map.entry("gif", "image/gif"),
            java.util.Map.entry("bmp", "image/bmp"));

    private final AdminAiKnowledgeService adminAiKnowledgeService;

    public AdminAiKnowledgeController(AdminAiKnowledgeService adminAiKnowledgeService) {
        this.adminAiKnowledgeService = adminAiKnowledgeService;
    }

    @GetMapping(DOCUMENTS)
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "AI 知识库文档分页列表")
    public ResponseEntity<HttpMessageBody<PageResult<AdminAiKnowledgeListItemDto>>> list(
            @PathVariable long siteId,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long baseId) {
        PageResult<AdminAiKnowledgeListItemDto> data = adminAiKnowledgeService.listDocuments(
                siteId, new AdminAiKnowledgeListQuery(pageNum, pageSize, status, baseId));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }

    private static final String BASES = "/api/admin/sites/{siteId}/ai/knowledge-bases";

    @GetMapping(BASES)
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "本站可见知识库（自有＋被共享）")
    public ResponseEntity<HttpMessageBody<java.util.List<AdminAiKnowledgeDtos.AdminAiKnowledgeBaseDto>>> listBases(
            @PathVariable long siteId) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.listBasesForSite(siteId)));
    }

    @PostMapping(BASES)
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:create')")
    @Operation(summary = "新建知识库")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDtos.AdminAiKnowledgeBaseDto>> createBase(
            @PathVariable long siteId,
            @Valid @RequestBody AdminAiKnowledgeDtos.AdminAiKnowledgeBaseCreateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.createBase(siteId, request)));
    }

    @DeleteMapping(BASES + "/{baseId}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:delete')")
    @Operation(summary = "删除知识库（默认库与非空库不可删）")
    public ResponseEntity<HttpMessageBody<Void>> deleteBase(
            @PathVariable long siteId, @PathVariable long baseId) {
        adminAiKnowledgeService.deleteBase(siteId, baseId);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }

    @GetMapping("/api/admin/ai/knowledge-bases")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "全平台知识库列表（知识中心）")
    public ResponseEntity<HttpMessageBody<java.util.List<AdminAiKnowledgeDtos.AdminAiKnowledgeBaseDto>>> listAllBases() {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.listAllBases()));
    }

    @PostMapping("/api/admin/ai/knowledge-bases/{baseId}/links/{targetSiteId}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:update')")
    @Operation(summary = "把知识库共享给目标站点（文档扇出同步）")
    public ResponseEntity<HttpMessageBody<Void>> linkBase(
            @PathVariable long baseId, @PathVariable long targetSiteId) {
        adminAiKnowledgeService.linkBase(baseId, targetSiteId);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }

    @DeleteMapping("/api/admin/ai/knowledge-bases/{baseId}/links/{targetSiteId}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:update')")
    @Operation(summary = "取消知识库对目标站点的共享（文档从其分区移除）")
    public ResponseEntity<HttpMessageBody<Void>> unlinkBase(
            @PathVariable long baseId, @PathVariable long targetSiteId) {
        adminAiKnowledgeService.unlinkBase(baseId, targetSiteId);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }

    @GetMapping(DOCUMENTS + "/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "AI 知识库文档详情")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDetailDto>> get(
            @PathVariable long siteId, @PathVariable("id") long id) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.getDocument(siteId, id)));
    }

    @PostMapping(DOCUMENTS)
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:create')")
    @Operation(summary = "创建 AI 知识库文档")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDetailDto>> create(
            @PathVariable long siteId, @Valid @RequestBody AdminAiKnowledgeCreateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.createDocument(siteId, request)));
    }

    @PostMapping(value = DOCUMENTS + "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:create')")
    @Operation(summary = "上传 AI 知识库文件（多格式，归一化为 Markdown 入库，原档留存对象存储）")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDetailDto>> upload(
            @PathVariable long siteId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "baseId", required = false) Long baseId) {
        UploadPayload payload = toUploadPayload(siteId, file, title, baseId);
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS,
                adminAiKnowledgeService.createUploadedDocument(siteId, payload.request(), payload.meta())));
    }

    @GetMapping(DOCUMENTS + "/{id}/file")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "AI 知识库文档原档下载（短时 presigned URL）")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeFileDto>> originalFile(
            @PathVariable long siteId, @PathVariable("id") long id) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.originalFileUrl(siteId, id)));
    }

    private record UploadPayload(
            AdminAiKnowledgeCreateRequest request, AdminAiKnowledgeDtos.AdminAiKnowledgeUploadMeta meta) {}

    private UploadPayload toUploadPayload(long siteId, MultipartFile file, String titleOverride, Long baseId) {
        if (file == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_REQUIRED);
        }
        String filename = baseName(file.getOriginalFilename());
        String extension = extensionOf(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_TYPE);
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_SIZE);
        }
        byte[] bytes = readBytes(file);
        if (bytes.length > MAX_UPLOAD_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_SIZE);
        }
        String content;
        if (IMAGE_EXTENSIONS.contains(extension)) {
            // 图片没有文本层可抽：直接经站点生效模型链视觉转写；为空按 FILE_EMPTY 拒绝。
            content =
                    adminAiKnowledgeService.transcribeImage(
                            siteId, bytes, CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"));
        } else {
            content = convertContent(extension, bytes);
            if (!StringUtils.hasText(content) && "pdf".equals(extension)) {
                // 扫描版 PDF 无文本层：经站点生效模型链转写；仍为空才按 FILE_EMPTY 拒绝。
                content = adminAiKnowledgeService.transcribeScannedPdf(siteId, bytes);
            }
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_EMPTY);
        }
        if (content.length() > MAX_CONTENT_CHARS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_SIZE);
        }
        String title = StringUtils.hasText(titleOverride) ? titleOverride.trim() : titleFromFilename(filename);
        if (!StringUtils.hasText(title) || title.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.TITLE_INVALID);
        }
        AdminAiKnowledgeDtos.AdminAiKnowledgeUploadMeta meta = new AdminAiKnowledgeDtos.AdminAiKnowledgeUploadMeta(
                filename,
                extension,
                CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"),
                bytes.length,
                sha256Hex(bytes),
                bytes);
        return new UploadPayload(new AdminAiKnowledgeCreateRequest(title, content, baseId), meta);
    }

    private static String convertContent(String extension, byte[] bytes) {
        try {
            return AdminAiKnowledgeContentConverter.toStoredContent(extension, bytes);
        } catch (AdminAiKnowledgeTextExtractor.ExtractionException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_ENCODING);
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_ENCODING);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String baseName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        String name = originalFilename.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String titleFromFilename(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0) {
            return "";
        }
        return filename.substring(0, dot).trim();
    }

    @PutMapping(DOCUMENTS + "/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:update')")
    @Operation(summary = "更新 AI 知识库文档")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDetailDto>> update(
            @PathVariable long siteId,
            @PathVariable("id") long id,
            @Valid @RequestBody AdminAiKnowledgeUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.updateDocument(siteId, id, request)));
    }

    @PostMapping(DOCUMENTS + "/{id}/reindex")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:update')")
    @Operation(summary = "重新索引单篇 AI 知识库文档（同步派生向量索引并记录结果）")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDetailDto>> reindex(
            @PathVariable long siteId, @PathVariable("id") long id) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.reindexDocument(siteId, id)));
    }

    @DeleteMapping(DOCUMENTS + "/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:delete')")
    @Operation(summary = "删除 AI 知识库文档（软删除，并移除派生索引）")
    public ResponseEntity<HttpMessageBody<Void>> delete(
            @PathVariable long siteId, @PathVariable("id") long id) {
        adminAiKnowledgeService.deleteDocument(siteId, id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }

    @GetMapping("/api/admin/ai/knowledge-overview")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "知识中心总览（全平台文档/索引健康/原档用量）")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDtos.AdminAiKnowledgeOverviewDto>> overview() {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.knowledgeOverview()));
    }

    @PostMapping("/api/admin/ai/knowledge/reindex-failed")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:update')")
    @Operation(summary = "批量重试全部索引失败的知识库文档")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDtos.AdminAiKnowledgeReindexFailedResultDto>>
            reindexFailed() {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.reindexFailedDocuments()));
    }

    @PostMapping("/api/admin/ai/knowledge/reembed")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:update')")
    @Operation(summary = "按各站点当前模型入口重嵌全部知识库文档")
    public ResponseEntity<?> reembed() {
        try {
            AdminAiKnowledgeReembedResultDto result = adminAiKnowledgeService.reembedAll();
            return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, result));
        } catch (ReembedConflictException ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(new AdminAiKnowledgeReembedConflictResponse(
                            "各站点的 embedding 维度不一致，未改动任何向量数据。", ex.conflicts()));
        }
    }
}
