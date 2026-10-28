package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiConversationListItemDto;
import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiConversationListQuery;
import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiMessageDto;
import com.mywebsite.common.page.PageResult;
import java.util.List;

public interface AdminAiConversationService {

    PageResult<AdminAiConversationListItemDto> listConversations(long agentId, AdminAiConversationListQuery query);

    List<AdminAiMessageDto> listMessages(long agentId, long conversationId);

    void softDeleteByAgentId(long agentId);
}
