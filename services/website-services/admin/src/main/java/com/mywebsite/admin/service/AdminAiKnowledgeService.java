package com.mywebsite.admin.service;

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
}
