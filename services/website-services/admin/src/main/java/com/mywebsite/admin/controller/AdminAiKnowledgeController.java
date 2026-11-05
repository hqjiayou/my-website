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
import com.mywebsite.admin.service.AdminAiKnowledgeService;
import com.mywebsite.infra.knowledge.KnowledgeContentConverter;
import com.mywebsite.infra.knowledge.KnowledgeTextExtractor;
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

    private static final String DOCUMENTS = "/api/admin/agents/{agentId}/ai/knowledge-documents";
    private static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

    /** zip 批量导入上限：条目数与解压后总字节（防 zip 炸弹与整批拖垮请求）。 */
    private static final int MAX_ARCHIVE_ENTRIES = 100;

    private static final long MAX_ARCHIVE_TOTAL_BYTES = 50L * 1024 * 1024;
    /** 抽取文本上限（字符），与 agent-services 的 MAX_CONTENT_LENGTH 对齐。 */
    private static final int MAX_CONTENT_CHARS = 3_000_000;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "text", "rst", "adoc", "asciidoc", "org", "tex",
            "csv", "tsv", "json", "xml", "yaml", "yml", "toml", "ini", "log",
            "jsonl", "ndjson", "properties", "conf", "cfg", "env", "diff", "patch",
            "py", "java", "js", "ts", "tsx", "jsx", "css", "scss", "sql", "sh",
            "bash", "zsh", "ps1", "mjs", "cjs",
            "go", "rs", "c", "h", "cpp", "hpp", "cs", "rb", "php", "swift", "kt", "kts",
            "scala", "pl", "r", "lua", "vue", "svelte", "dart", "groovy", "gradle",
            "hs", "ex", "exs", "tf", "proto",
            "pdf", "docx", "docm", "doc", "html", "htm", "xlsx", "xlsm", "xls",
            "pptx", "pptm", "ppt", "epub", "odt", "ods", "odp", "rtf",
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif");

    /** 图片扩展名：不走文本转换器，内容来自视觉转写（见 toUploadPayload）。 */
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif");

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
            java.util.Map.entry("bmp", "image/bmp"),
            java.util.Map.entry("heic", "image/heic"),
            java.util.Map.entry("heif", "image/heif"),
            java.util.Map.entry("doc", "application/msword"),
            java.util.Map.entry("xls", "application/vnd.ms-excel"),
            java.util.Map.entry("ppt", "application/vnd.ms-powerpoint"));

    private final AdminAiKnowledgeService adminAiKnowledgeService;

    public AdminAiKnowledgeController(AdminAiKnowledgeService adminAiKnowledgeService) {
        this.adminAiKnowledgeService = adminAiKnowledgeService;
    }

    @GetMapping(DOCUMENTS)
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "AI 知识库文档分页列表")
    public ResponseEntity<HttpMessageBody<PageResult<AdminAiKnowledgeListItemDto>>> list(
            @PathVariable long agentId,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long baseId) {
        PageResult<AdminAiKnowledgeListItemDto> data = adminAiKnowledgeService.listDocuments(
                agentId, new AdminAiKnowledgeListQuery(pageNum, pageSize, status, baseId));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }

    private static final String BASES = "/api/admin/agents/{agentId}/ai/knowledge-bases";

    @GetMapping(BASES)
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "本站可见知识库（自有＋被共享）")
    public ResponseEntity<HttpMessageBody<java.util.List<AdminAiKnowledgeDtos.AdminAiKnowledgeBaseDto>>> listBases(
            @PathVariable long agentId) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.listBasesForSite(agentId)));
    }

    @PostMapping(BASES)
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "新建知识库")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDtos.AdminAiKnowledgeBaseDto>> createBase(
            @PathVariable long agentId,
            @Valid @RequestBody AdminAiKnowledgeDtos.AdminAiKnowledgeBaseCreateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.createBase(agentId, request)));
    }

    @DeleteMapping(BASES + "/{baseId}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "删除知识库（默认库与非空库不可删）")
    public ResponseEntity<HttpMessageBody<Void>> deleteBase(
            @PathVariable long agentId, @PathVariable long baseId) {
        adminAiKnowledgeService.deleteBase(agentId, baseId);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }

    @GetMapping("/api/admin/ai/knowledge-bases")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "全平台知识库列表（知识中心）")
    public ResponseEntity<HttpMessageBody<java.util.List<AdminAiKnowledgeDtos.AdminAiKnowledgeBaseDto>>> listAllBases() {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.listAllBases()));
    }

    @PostMapping("/api/admin/ai/knowledge-bases/{baseId}/links/{targetAgentId}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "把知识库共享（订阅）给目标站点")
    public ResponseEntity<HttpMessageBody<Void>> linkBase(
            @PathVariable long baseId, @PathVariable long targetAgentId) {
        adminAiKnowledgeService.linkBase(baseId, targetAgentId);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }

    @DeleteMapping("/api/admin/ai/knowledge-bases/{baseId}/links/{targetAgentId}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "取消知识库对目标站点的共享（订阅）")
    public ResponseEntity<HttpMessageBody<Void>> unlinkBase(
            @PathVariable long baseId, @PathVariable long targetAgentId) {
        adminAiKnowledgeService.unlinkBase(baseId, targetAgentId);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }

    @GetMapping(DOCUMENTS + "/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "AI 知识库文档详情")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDetailDto>> get(
            @PathVariable long agentId, @PathVariable("id") long id) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.getDocument(agentId, id)));
    }

    @PostMapping(DOCUMENTS)
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "创建 AI 知识库文档")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDetailDto>> create(
            @PathVariable long agentId, @Valid @RequestBody AdminAiKnowledgeCreateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.createDocument(agentId, request)));
    }

    @PostMapping(value = DOCUMENTS + "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "上传 AI 知识库文件（多格式，归一化为 Markdown 入库，原档留存对象存储）")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDetailDto>> upload(
            @PathVariable long agentId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "baseId", required = false) Long baseId) {
        UploadPayload payload = toUploadPayload(agentId, file, title, baseId);
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS,
                adminAiKnowledgeService.createUploadedDocument(agentId, payload.request(), payload.meta())));
    }

    @PostMapping(value = DOCUMENTS + "/upload-archive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "上传 zip 批量导入 AI 知识库文档（逐条转换入库，部分失败不回滚）")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDtos.AdminAiKnowledgeArchiveResultDto>> uploadArchive(
            @PathVariable long agentId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "baseId", required = false) Long baseId) {
        if (file == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_REQUIRED);
        }
        if (!"zip".equals(extensionOf(baseName(file.getOriginalFilename())))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_TYPE);
        }
        byte[] zipBytes = readBytes(file);
        java.util.List<AdminAiKnowledgeDetailDto> documents = new java.util.ArrayList<>();
        java.util.List<AdminAiKnowledgeDtos.AdminAiKnowledgeArchiveFailureDto> failures =
                new java.util.ArrayList<>();
        long totalBytes = 0;
        int entries = 0;
        try (java.util.zip.ZipInputStream zip =
                new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryPath = entry.getName();
                String entryName = baseName(entryPath);
                // 系统垃圾（macOS 资源叉、缩略图缓存、隐藏文件）不算用户文档，静默跳过
                if (entryName.isBlank()
                        || entryName.startsWith(".")
                        || entryPath.startsWith("__MACOSX")) {
                    continue;
                }
                entries++;
                if (entries > MAX_ARCHIVE_ENTRIES) {
                    failures.add(new AdminAiKnowledgeDtos.AdminAiKnowledgeArchiveFailureDto(
                            entryName, AdminAiKnowledgeMessageKeys.FILE_SIZE));
                    break;
                }
                byte[] entryBytes = zip.readAllBytes();
                totalBytes += entryBytes.length;
                if (totalBytes > MAX_ARCHIVE_TOTAL_BYTES) {
                    failures.add(new AdminAiKnowledgeDtos.AdminAiKnowledgeArchiveFailureDto(
                            entryName, AdminAiKnowledgeMessageKeys.FILE_SIZE));
                    break;
                }
                try {
                    UploadPayload payload = toUploadPayload(agentId, entryName, entryBytes, null, baseId);
                    documents.add(adminAiKnowledgeService.createUploadedDocument(
                            agentId, payload.request(), payload.meta()));
                } catch (BusinessException ex) {
                    failures.add(new AdminAiKnowledgeDtos.AdminAiKnowledgeArchiveFailureDto(
                            entryName, ex.getMessageKey()));
                }
            }
        } catch (java.io.IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_ENCODING);
        }
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS,
                new AdminAiKnowledgeDtos.AdminAiKnowledgeArchiveResultDto(
                        documents.size(), documents, failures)));
    }

    @GetMapping(DOCUMENTS + "/{id}/file")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "AI 知识库文档原档下载（短时 presigned URL）")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeFileDto>> originalFile(
            @PathVariable long agentId, @PathVariable("id") long id) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.originalFileUrl(agentId, id)));
    }

    private record UploadPayload(
            AdminAiKnowledgeCreateRequest request, AdminAiKnowledgeDtos.AdminAiKnowledgeUploadMeta meta) {}

    private UploadPayload toUploadPayload(long agentId, MultipartFile file, String titleOverride, Long baseId) {
        if (file == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_REQUIRED);
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_SIZE);
        }
        return toUploadPayload(agentId, baseName(file.getOriginalFilename()), readBytes(file), titleOverride, baseId);
    }

    private UploadPayload toUploadPayload(
            long agentId, String filename, byte[] bytes, String titleOverride, Long baseId) {
        String extension = extensionOf(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_TYPE);
        }
        if (bytes.length > MAX_UPLOAD_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_SIZE);
        }
        String content;
        if (IMAGE_EXTENSIONS.contains(extension)) {
            // 图片没有文本层可抽：直接经站点生效模型链视觉转写；为空按 FILE_EMPTY 拒绝。
            content =
                    adminAiKnowledgeService.transcribeImage(
                            agentId, bytes, CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"));
        } else {
            content = convertContent(extension, bytes);
            if (!StringUtils.hasText(content) && "pdf".equals(extension)) {
                // 扫描版 PDF 无文本层：经站点生效模型链转写；仍为空才按 FILE_EMPTY 拒绝。
                content = adminAiKnowledgeService.transcribeScannedPdf(agentId, bytes);
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
            return KnowledgeContentConverter.toStoredContent(extension, bytes);
        } catch (KnowledgeTextExtractor.ExtractionException ex) {
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
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "更新 AI 知识库文档")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDetailDto>> update(
            @PathVariable long agentId,
            @PathVariable("id") long id,
            @Valid @RequestBody AdminAiKnowledgeUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.updateDocument(agentId, id, request)));
    }

    @PostMapping(DOCUMENTS + "/{id}/reindex")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "重新索引单篇 AI 知识库文档（同步派生向量索引并记录结果）")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDetailDto>> reindex(
            @PathVariable long agentId, @PathVariable("id") long id) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.reindexDocument(agentId, id)));
    }

    @DeleteMapping(DOCUMENTS + "/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "删除 AI 知识库文档（软删除，并移除派生索引）")
    public ResponseEntity<HttpMessageBody<Void>> delete(
            @PathVariable long agentId, @PathVariable("id") long id) {
        adminAiKnowledgeService.deleteDocument(agentId, id);
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
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "批量重试全部索引失败的知识库文档")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDtos.AdminAiKnowledgeReindexFailedResultDto>>
            reindexFailed() {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.reindexFailedDocuments()));
    }

    @PostMapping("/api/admin/ai/knowledge/reembed")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
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

    @PostMapping("/api/admin/ai/knowledge/rebuild-all-spaces")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:list')")
    @Operation(summary = "存量迁移收官：清扫旧 agent 分区、按知识空间全量重建索引并回对账报告")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDtos.AdminAiKnowledgeRebuildReportDto>>
            rebuildAllSpaces() {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, adminAiKnowledgeService.rebuildAllSpaces()));
    }
}
