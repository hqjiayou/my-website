package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiConversationListItemDto;
import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiConversationListQuery;
import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiMessageDto;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.common.page.PageResult;
import java.util.List;

public interface AdminAiConversationService {

    PageResult<AdminAiConversationListItemDto> listConversations(long agentId, AdminAiConversationListQuery query);

    List<AdminAiMessageDto> listMessages(long agentId, long conversationId);

    void softDeleteByAgentId(long agentId);

    /** 校验续写目标：存在、属于该站点且 source=ADMIN_PREVIEW，否则 NOT_FOUND（不泄露 PORTAL 会话）。 */
    void requirePreviewConversation(long agentId, long conversationId);

    /**
     * 后台试聊一轮 DONE 成功后落库：{@code conversationId} 为空则新建 ADMIN_PREVIEW 会话
     * （agent_user_id=NULL，标题取首条用户消息截断），否则续写；写 USER+ASSISTANT 两条消息，
     * {@code runMeta} 序列化进 ASSISTANT agent_meta。不写 user memory。返回会话 id。
     */
    long recordPreviewExchange(
            long agentId, Long conversationId, String userMessage, String reply, AgentRunMeta runMeta);
}
