package com.mywebsite.agent.client;

import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentPingResponse;
import com.mywebsite.agent.dto.AgentRetrievalDocument;

public interface AgentClient {

    AgentPingResponse ping();

    AgentChatResponse chat(AgentChatRequest request);

    /**
     * 流式 chat：转发 agent-services 的应用层 SSE 事件。
     * 连接前校验失败抛 BusinessException；流中错误以 ERROR 事件送达 listener。
     */
    void chatStream(AgentChatRequest request, AgentStreamListener listener);

    /** KB 派生索引同步（同 sourceId 覆盖）；检索未配置时抛 EXTERNAL_DEPENDENCY_BLOCKED。 */
    void upsertRetrievalDocument(AgentRetrievalDocument document);

    /** 从派生索引移除文档。 */
    void deleteRetrievalDocument(String siteId, String sourceId);
}
