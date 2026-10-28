package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiKnowledgeDtos;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeDetailDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeListItemDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeListQuery;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeReembedConflictDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeReembedResultDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeUpdateRequest;
import com.mywebsite.common.page.PageResult;
import java.util.List;

public interface AdminAiKnowledgeService {

    /** 各站点探针维度不一致。HTTP 层回 422，且不得改向量数据。 */
    final class ReembedConflictException extends RuntimeException {

        private final List<AdminAiKnowledgeReembedConflictDto> conflicts;

        public ReembedConflictException(List<AdminAiKnowledgeReembedConflictDto> conflicts) {
            super("embedding dimensions conflict");
            this.conflicts = List.copyOf(conflicts);
        }

        public List<AdminAiKnowledgeReembedConflictDto> conflicts() {
            return conflicts;
        }
    }

    PageResult<AdminAiKnowledgeListItemDto> listDocuments(long siteId, AdminAiKnowledgeListQuery query);

    AdminAiKnowledgeDetailDto getDocument(long siteId, long documentId);

    AdminAiKnowledgeDetailDto createDocument(long siteId, AdminAiKnowledgeCreateRequest request);

    /**
     * 扫描版 PDF 转写：PDF 文本层抽取为空时，经站点生效模型链做原生文档理解。
     * 无可用模型链、调用失败或结果为空一律回空串（上传侧据此维持 FILE_EMPTY 诚实拒绝）。
     */
    String transcribeScannedPdf(long siteId, byte[] pdfBytes);

    /**
     * 图片转写：图片上传（截图/扫描件/照片）经站点生效模型链做视觉转写。
     * 失败语义同 {@link #transcribeScannedPdf}：一律回空串，上传侧维持诚实拒绝。
     */
    String transcribeImage(long siteId, byte[] imageBytes, String mediaType);

    AdminAiKnowledgeDetailDto updateDocument(
            long siteId, long documentId, AdminAiKnowledgeUpdateRequest request);

    void deleteDocument(long siteId, long documentId);

    /** 站点删除时软删文档，并 best-effort 移除派生索引。 */
    void softDeleteBySiteId(long siteId);

    /**
     * 按各站点当前模型入口重嵌全部 ACTIVE 文档。
     * 维度冲突抛 {@link ReembedConflictException}，不重建、不 upsert。
     */
    AdminAiKnowledgeReembedResultDto reembedAll();

    /** 重新同步单篇文档的派生索引，并落库 INDEXED/FAILED 结果。 */
    AdminAiKnowledgeDetailDto reindexDocument(long siteId, long documentId);

    /** 上传建档：同站点同 SHA-256 去重、原档落对象存储（已配置时）、再走建档与索引同步。 */
    AdminAiKnowledgeDetailDto createUploadedDocument(
            long siteId,
            AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest request,
            AdminAiKnowledgeDtos.AdminAiKnowledgeUploadMeta meta);

    /** 原档短时下载 URL；未保存原档时抛 FILE_ORIGINAL_MISSING。 */
    AdminAiKnowledgeDtos.AdminAiKnowledgeFileDto originalFileUrl(long siteId, long documentId);

    /** 知识中心总览：全平台文档/索引健康/原档用量聚合 + 分站点统计 + 最近失败。 */
    AdminAiKnowledgeDtos.AdminAiKnowledgeOverviewDto knowledgeOverview();

    /** 批量重试全部「索引失败且启用中」的文档，返回尝试/成功数。 */
    AdminAiKnowledgeDtos.AdminAiKnowledgeReindexFailedResultDto reindexFailedDocuments();

    /** 本站可见的知识库：自有 + 被共享链接的（只读）。 */
    java.util.List<AdminAiKnowledgeDtos.AdminAiKnowledgeBaseDto> listBasesForSite(long siteId);

    /** 全平台知识库列表（知识中心管理用）。 */
    java.util.List<AdminAiKnowledgeDtos.AdminAiKnowledgeBaseDto> listAllBases();

    AdminAiKnowledgeDtos.AdminAiKnowledgeBaseDto createBase(
            long siteId, AdminAiKnowledgeDtos.AdminAiKnowledgeBaseCreateRequest request);

    /** 删除知识库：默认库不可删、非空不可删（先移走/删除其文档）。 */
    void deleteBase(long siteId, long baseId);

    /** 把知识库共享链接给目标站点，并把其 ACTIVE 文档扇出同步到目标分区。 */
    void linkBase(long baseId, long targetSiteId);

    /** 取消共享链接，并把该库文档从目标分区移除。 */
    void unlinkBase(long baseId, long targetSiteId);
}
