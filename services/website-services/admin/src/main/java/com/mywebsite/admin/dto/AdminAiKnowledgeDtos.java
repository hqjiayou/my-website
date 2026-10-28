package com.mywebsite.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class AdminAiKnowledgeDtos {

    private AdminAiKnowledgeDtos() {}

    public record AdminAiKnowledgeListItemDto(
            Long id,
            Long siteId,
            Long baseId,
            boolean owned,
            String title,
            String status,
            String indexStatus,
            String indexError,
            OffsetDateTime indexedAt,
            String sourceFormat,
            String originalFilename,
            Long fileSizeBytes,
            boolean hasOriginal,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminAiKnowledgeDetailDto(
            Long id,
            Long siteId,
            Long baseId,
            boolean owned,
            String title,
            String content,
            String status,
            String indexStatus,
            String indexError,
            OffsetDateTime indexedAt,
            String sourceFormat,
            String originalFilename,
            Long fileSizeBytes,
            boolean hasOriginal,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    /** 上传元信息（原档留存与去重）：bytes 为原始文件内容，sha256 为其十六进制指纹。 */
    public record AdminAiKnowledgeUploadMeta(
            String originalFilename,
            String sourceFormat,
            String contentType,
            long fileSizeBytes,
            String sha256,
            byte[] bytes) {}

    /** 原档下载（短时 presigned URL）。 */
    public record AdminAiKnowledgeFileDto(String url, String filename, String sourceFormat) {}

    /** zip 批量导入中单个条目的失败（文件名＋原因 messageKey，与单文件上传的报错口径一致）。 */
    public record AdminAiKnowledgeArchiveFailureDto(String fileName, String reason) {}

    /** zip 批量导入结果：成功创建的文档明细＋逐条失败清单（部分成功是常态，不整批回滚）。 */
    public record AdminAiKnowledgeArchiveResultDto(
            int createdCount,
            java.util.List<AdminAiKnowledgeDetailDto> documents,
            java.util.List<AdminAiKnowledgeArchiveFailureDto> failures) {}

    /** 知识中心总览：全平台聚合 + 分站点统计 + 最近索引失败。 */
    public record AdminAiKnowledgeOverviewDto(
            long totalDocuments,
            long indexedDocuments,
            long failedDocuments,
            long pendingDocuments,
            long originalBytes,
            List<AdminAiKnowledgeSiteStatDto> sites,
            List<AdminAiKnowledgeFailureDto> recentFailures) {}

    public record AdminAiKnowledgeSiteStatDto(
            Long siteId,
            String siteName,
            long totalDocuments,
            long indexedDocuments,
            long failedDocuments,
            long originalBytes) {}

    public record AdminAiKnowledgeFailureDto(
            Long documentId,
            Long siteId,
            String siteName,
            String title,
            String indexError,
            OffsetDateTime updatedAt) {}

    /** 批量重试索引失败文档的结果。 */
    public record AdminAiKnowledgeReindexFailedResultDto(int attempted, int succeeded) {}

    public record AdminAiKnowledgeCreateRequest(
            @NotBlank @Size(max = 255) String title, @NotBlank String content, Long baseId) {

        public AdminAiKnowledgeCreateRequest(String title, String content) {
            this(title, content, null);
        }
    }

    /** 知识库（base）：owned=false 表示经共享链接可见（只读）。 */
    public record AdminAiKnowledgeBaseDto(
            Long id,
            Long siteId,
            String ownerSiteName,
            String name,
            boolean isDefault,
            boolean owned,
            long documentCount,
            List<Long> linkedSiteIds,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminAiKnowledgeBaseCreateRequest(@NotBlank @Size(max = 128) String name) {}

    public record AdminAiKnowledgeUpdateRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank String content,
            @NotBlank String status,
            Integer version) {}

    public record AdminAiKnowledgeListQuery(Integer pageNum, Integer pageSize, String status, Long baseId) {

        public AdminAiKnowledgeListQuery(Integer pageNum, Integer pageSize, String status) {
            this(pageNum, pageSize, status, null);
        }
    }

    public record AdminAiKnowledgeReembedConflictDto(Long siteId, Integer dimensions) {}

    public record AdminAiKnowledgeReembedFailureDto(Long siteId, String sourceId, String error) {}

    public record AdminAiKnowledgeReembedResultDto(
            int sites,
            int documents,
            Integer dimensions,
            boolean recreatedSchema,
            List<AdminAiKnowledgeReembedFailureDto> failed,
            String status) {}

    public record AdminAiKnowledgeReembedConflictResponse(
            String message, List<AdminAiKnowledgeReembedConflictDto> conflicts) {}
}
