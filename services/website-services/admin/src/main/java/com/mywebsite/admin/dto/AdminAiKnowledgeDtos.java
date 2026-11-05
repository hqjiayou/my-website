package com.mywebsite.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class AdminAiKnowledgeDtos {

    private AdminAiKnowledgeDtos() {}

    public record AdminAiKnowledgeListItemDto(
            Long id,
            Long agentId,
            Long baseId,
            boolean owned,
            String title,
            String status,
            String indexStatus,
            String indexError,
            OffsetDateTime indexedAt,
            String indexEmbeddingSpec,
            boolean indexStale,
            String sourceFormat,
            String originalFilename,
            Long fileSizeBytes,
            boolean hasOriginal,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            int unevaluatedFormulaCount) {}

    public record AdminAiKnowledgeDetailDto(
            Long id,
            Long agentId,
            Long baseId,
            boolean owned,
            String title,
            String content,
            String status,
            String indexStatus,
            String indexError,
            OffsetDateTime indexedAt,
            String indexEmbeddingSpec,
            boolean indexStale,
            String sourceFormat,
            String originalFilename,
            Long fileSizeBytes,
            boolean hasOriginal,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            int unevaluatedFormulaCount) {}

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
            /** 已索引文档中，记录的嵌入类名集合与当前登记类集合不一致的数量（存量索引已过期）。 */
            long staleDocuments,
            long originalBytes,
            List<AdminAiKnowledgeAgentStatDto> sites,
            List<AdminAiKnowledgeFailureDto> recentFailures,
            /** Issue #203：删除失败待重试的 tombstone 数——这些文档已删但向量残留仍可能被命中。 */
            long pendingRetrievalDeletes,
            /** 最早一条未解决同步问题的时间；无问题时为 null。 */
            java.time.OffsetDateTime oldestSyncIssueAt) {}

    public record AdminAiKnowledgeAgentStatDto(
            Long agentId,
            String agentName,
            long totalDocuments,
            long indexedDocuments,
            long failedDocuments,
            long originalBytes) {}

    public record AdminAiKnowledgeFailureDto(
            Long documentId,
            Long agentId,
            String agentName,
            String title,
            String indexError,
            OffsetDateTime updatedAt) {}

    /** 批量重试索引失败文档的结果。 */
    public record AdminAiKnowledgeReindexFailedResultDto(int attempted, int succeeded) {}

    /**
     * 全量重建对账报告中的单空间行（Issue #209 UoW-5）：{@code documents} 为该空间
     * ACTIVE 文档数（本轮重建的覆盖面）；{@code indexedSources}/{@code chunks} 取自
     * 执行层分区统计，取不到时为 -1（未能取得，不代表为零）；{@code syncFailures}
     * 为本轮同步后未达 INDEXED 的文档数。
     */
    public record AdminAiKnowledgeRebuildSpaceDto(
            Long baseId,
            long documents,
            long indexedSources,
            long chunks,
            long syncFailures) {}

    /** 旧分区残留：以旧 agent id 为键的分区在清扫＋重建后仍有索引源（且该 id 不是现存空间）。 */
    public record AdminAiKnowledgeRebuildResidueDto(Long partitionKey, long indexedSources) {}

    /**
     * 全量重建对账报告（Issue #209 UoW-5）：文档/块/spec 三数合计中，文档数＝各空间
     * ACTIVE 文档数之和，块数＝各空间执行层块行数之和（取不到的空间按 0 计、其行内
     * 值为 -1），spec 数＝本轮覆盖文档中同步后 INDEXED 且已记
     * {@code index_embedding_spec} 的数量。{@code chunksUnknownSpaces}＝分区统计
     * 取不到（块数未知）的空间数：非 0 时 {@code totalChunks} 是低报，须结合此
     * 字段读（解耦深审）。
     */
    public record AdminAiKnowledgeRebuildReportDto(
            List<AdminAiKnowledgeRebuildSpaceDto> spaces,
            long totalDocuments,
            long totalChunks,
            long totalSpecs,
            long totalSyncFailures,
            List<AdminAiKnowledgeRebuildResidueDto> legacyResidues,
            long chunksUnknownSpaces) {}

    public record AdminAiKnowledgeCreateRequest(
            @NotBlank @Size(max = 255) String title, @NotBlank String content, Long baseId) {

        public AdminAiKnowledgeCreateRequest(String title, String content) {
            this(title, content, null);
        }
    }

    /** 知识库（base）：owned=false 表示经共享链接可见（只读）。 */
    public record AdminAiKnowledgeBaseDto(
            Long id,
            Long agentId,
            String ownerAgentName,
            String name,
            boolean isDefault,
            boolean owned,
            long documentCount,
            List<Long> linkedAgentIds,
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

    public record AdminAiKnowledgeReembedConflictDto(Long agentId, Integer dimensions) {}

    public record AdminAiKnowledgeReembedFailureDto(Long agentId, String sourceId, String error) {}

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
