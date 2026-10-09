package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeDetailDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeListItemDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeListQuery;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeUpdateRequest;
import com.mywebsite.common.page.PageResult;

public interface AdminAiKnowledgeService {

    PageResult<AdminAiKnowledgeListItemDto> listDocuments(long siteId, AdminAiKnowledgeListQuery query);

    AdminAiKnowledgeDetailDto getDocument(long siteId, long documentId);

    AdminAiKnowledgeDetailDto createDocument(long siteId, AdminAiKnowledgeCreateRequest request);

    AdminAiKnowledgeDetailDto updateDocument(
            long siteId, long documentId, AdminAiKnowledgeUpdateRequest request);

    void deleteDocument(long siteId, long documentId);

    /** 站点删除时软删文档，并 best-effort 移除派生索引。 */
    void softDeleteBySiteId(long siteId);
}
