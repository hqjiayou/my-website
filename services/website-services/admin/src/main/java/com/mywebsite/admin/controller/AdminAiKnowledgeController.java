package com.mywebsite.admin.controller;

import com.mywebsite.admin.constant.AdminAiKnowledgeMessageKeys;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeDetailDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeListItemDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeListQuery;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeReembedConflictResponse;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeReembedResultDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeUpdateRequest;
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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md");
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "pdf", "docx", "html", "htm");

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
            @RequestParam(required = false) String status) {
        PageResult<AdminAiKnowledgeListItemDto> data = adminAiKnowledgeService.listDocuments(
                siteId, new AdminAiKnowledgeListQuery(pageNum, pageSize, status));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
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
    @Operation(summary = "上传 AI 知识库文件（.txt/.md/.pdf/.docx/.html），抽取文本走创建文档")
    public ResponseEntity<HttpMessageBody<AdminAiKnowledgeDetailDto>> upload(
            @PathVariable long siteId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS,
                adminAiKnowledgeService.createDocument(siteId, toCreateRequest(file, title))));
    }

    private static AdminAiKnowledgeCreateRequest toCreateRequest(MultipartFile file, String titleOverride) {
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
        String content = TEXT_EXTENSIONS.contains(extension) ? decodeUtf8(bytes) : extractText(extension, bytes);
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
        return new AdminAiKnowledgeCreateRequest(title, content);
    }

    private static String extractText(String extension, byte[] bytes) {
        try {
            return AdminAiKnowledgeTextExtractor.extractText(extension, bytes);
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

    private static String decodeUtf8(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            if (text.startsWith("\uFEFF")) {
                return text.substring(1);
            }
            return text;
        } catch (CharacterCodingException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.FILE_ENCODING);
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

    @DeleteMapping(DOCUMENTS + "/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:ai-kb:delete')")
    @Operation(summary = "删除 AI 知识库文档（软删除，并移除派生索引）")
    public ResponseEntity<HttpMessageBody<Void>> delete(
            @PathVariable long siteId, @PathVariable("id") long id) {
        adminAiKnowledgeService.deleteDocument(siteId, id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
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
