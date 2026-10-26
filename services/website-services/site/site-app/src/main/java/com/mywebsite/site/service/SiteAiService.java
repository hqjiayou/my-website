package com.mywebsite.site.service;

import com.mywebsite.site.dto.SiteAiChatRequestDto;
import com.mywebsite.site.dto.SiteAiChatResponseDto;
import com.mywebsite.site.dto.SiteKnowledgeDocumentDtos;
import com.mywebsite.site.dto.SiteKnowledgeSearchRequestDto;
import com.mywebsite.site.dto.SiteKnowledgeSearchResponseDto;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SiteAiService {

    SiteAiChatResponseDto chat(long siteId, SiteAiChatRequestDto request);

    SseEmitter chatStream(long siteId, SiteAiChatRequestDto request);

    SiteKnowledgeSearchResponseDto searchKnowledge(long siteId, SiteKnowledgeSearchRequestDto request);

    /** 本站 ACTIVE 知识文档列表（只读浏览，不含正文）。 */
    List<SiteKnowledgeDocumentDtos.ListItem> listKnowledgeDocuments(long siteId);

    /** 本站 ACTIVE 知识文档详情（Markdown 正文）；不存在/跨站/已停用统一 404。 */
    SiteKnowledgeDocumentDtos.Detail getKnowledgeDocument(long siteId, long documentId);
}
