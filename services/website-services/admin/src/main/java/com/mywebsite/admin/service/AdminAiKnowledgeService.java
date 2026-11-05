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

    PageResult<AdminAiKnowledgeListItemDto> listDocuments(long agentId, AdminAiKnowledgeListQuery query);

    AdminAiKnowledgeDetailDto getDocument(long agentId, long documentId);

    AdminAiKnowledgeDetailDto createDocument(long agentId, AdminAiKnowledgeCreateRequest request);

    /**
     * 扫描版 PDF 转写：PDF 文本层抽取为空时，经所选模式的生效链做原生文档理解
     * （Issue #208 UoW-M1：mode 由上传方显式指定，只调 {@code selectionChain(agentId, mode)}，
     * 备援封闭在模式内、不跨模式补链）。
     *
     * <p>失败三类分列，不再全吞成空串：mode 缺失/非法抛参数错误（MODE_INVALID）；
     * 所选模式无可用入口抛 {@code MODE_NO_AVAILABLE_ENTRY}；模型调用失败抛
     * {@code FILE_TRANSCRIBE_FAILED}（带失败性质）。只有转写结果本身为空才回空串，
     * 上传侧据此维持 FILE_EMPTY 诚实拒绝。
     */
    String transcribeScannedPdf(long agentId, String mode, byte[] pdfBytes);

    /**
     * 图片转写：图片上传（截图/扫描件/照片）经所选模式的生效链做视觉转写。
     * mode 口径与失败语义同 {@link #transcribeScannedPdf(long, String, byte[])}。
     */
    String transcribeImage(long agentId, String mode, byte[] imageBytes, String mediaType);

    AdminAiKnowledgeDetailDto updateDocument(
            long agentId, long documentId, AdminAiKnowledgeUpdateRequest request);

    void deleteDocument(long agentId, long documentId);

    /** 站点删除时软删文档，并 best-effort 移除派生索引。 */
    void softDeleteByAgentId(long agentId);

    /**
     * 按各站点当前模型入口重嵌全部 ACTIVE 文档。
     * 维度冲突抛 {@link ReembedConflictException}，不重建、不 upsert。
     */
    AdminAiKnowledgeReembedResultDto reembedAll();

    /** 重新同步单篇文档的派生索引，并落库 INDEXED/FAILED 结果。 */
    AdminAiKnowledgeDetailDto reindexDocument(long agentId, long documentId);

    /** 上传建档：同站点同 SHA-256 去重、原档落对象存储（已配置时）、再走建档与索引同步。 */
    AdminAiKnowledgeDetailDto createUploadedDocument(
            long agentId,
            AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest request,
            AdminAiKnowledgeDtos.AdminAiKnowledgeUploadMeta meta);

    /** 原档短时下载 URL；未保存原档时抛 FILE_ORIGINAL_MISSING。 */
    AdminAiKnowledgeDtos.AdminAiKnowledgeFileDto originalFileUrl(long agentId, long documentId);

    /** 知识中心总览：全平台文档/索引健康/原档用量聚合 + 分站点统计 + 最近失败。 */
    AdminAiKnowledgeDtos.AdminAiKnowledgeOverviewDto knowledgeOverview();

    /** Issue #203：定时重试派生索引同步问题行（tombstone 删除与链接分区同步）。 */
    void retryRetrievalSyncIssues();

    /** 批量重试全部「索引失败且启用中」的文档，返回尝试/成功数。 */
    AdminAiKnowledgeDtos.AdminAiKnowledgeReindexFailedResultDto reindexFailedDocuments();

    /** 本站可见的知识库：自有 + 被共享链接的（只读）。 */
    java.util.List<AdminAiKnowledgeDtos.AdminAiKnowledgeBaseDto> listBasesForSite(long agentId);

    /** 全平台知识库列表（知识中心管理用）。 */
    java.util.List<AdminAiKnowledgeDtos.AdminAiKnowledgeBaseDto> listAllBases();

    AdminAiKnowledgeDtos.AdminAiKnowledgeBaseDto createBase(
            long agentId, AdminAiKnowledgeDtos.AdminAiKnowledgeBaseCreateRequest request);

    /** 删除知识库：默认库不可删、非空不可删（先移走/删除其文档）。 */
    void deleteBase(long agentId, long baseId);

    /** 把知识库共享链接给目标站点，并把其 ACTIVE 文档扇出同步到目标分区。 */
    void linkBase(long baseId, long targetAgentId);

    /** 取消共享链接，并把该库文档从目标分区移除。 */
    void unlinkBase(long baseId, long targetAgentId);
}
