package com.mywebsite.agent.service;

import com.mywebsite.agent.dto.AgentAiChatRequestDto;
import com.mywebsite.agent.dto.AgentAiChatResponseDto;
import com.mywebsite.agent.dto.AgentKnowledgeDocumentDtos;
import com.mywebsite.agent.dto.AgentKnowledgeSearchRequestDto;
import com.mywebsite.agent.dto.AgentKnowledgeSearchResponseDto;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentAiService {

    AgentAiChatResponseDto chat(long agentId, AgentAiChatRequestDto request);

    SseEmitter chatStream(long agentId, AgentAiChatRequestDto request);

    AgentKnowledgeSearchResponseDto searchKnowledge(long agentId, AgentKnowledgeSearchRequestDto request);

    /** 本站 ACTIVE 知识文档列表（只读浏览，不含正文）。 */
    List<AgentKnowledgeDocumentDtos.ListItem> listKnowledgeDocuments(long agentId);

    /** 本站 ACTIVE 知识文档详情（Markdown 正文）；不存在/跨站/已停用统一 404。 */
    AgentKnowledgeDocumentDtos.Detail getKnowledgeDocument(long agentId, long documentId);
}
