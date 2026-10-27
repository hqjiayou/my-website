package com.mywebsite.agent.client;

import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentMcpReady;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.dto.AgentOcrPdfRequest;
import com.mywebsite.agent.dto.AgentOcrPdfResult;
import com.mywebsite.agent.dto.AgentPingResponse;
import com.mywebsite.agent.dto.AgentReembedPrepareResult;
import com.mywebsite.agent.dto.AgentRetrievalDocument;
import com.mywebsite.agent.dto.AgentRetrievalSearchRequest;
import com.mywebsite.agent.dto.AgentRetrievalSearchResult;
import com.mywebsite.agent.dto.AgentRuntimeStatus;
import com.mywebsite.agent.dto.AgentSkillSummary;
import java.util.List;

public interface AgentClient {

    AgentPingResponse ping();

    /** 运行时快照（Provider / Fallback / MCP / 预算）；不可达时抛 BusinessException。 */
    AgentRuntimeStatus runtimeStatus();

    /**
     * MCP 深检。进程可达时返回探测明细（含 503 的 servers）；连不上 agent-services 才抛 BusinessException。
     */
    AgentMcpReady mcpReady();

    AgentChatResponse chat(AgentChatRequest request);

    /** agent-services 技能目录（平台能力，与站点无关）。 */
    List<AgentSkillSummary> listSkills();

    /**
     * 流式 chat：转发 agent-services 的应用层 SSE 事件。
     * 连接前校验失败抛 BusinessException；流中错误以 ERROR 事件送达 listener。
     */
    void chatStream(AgentChatRequest request, AgentStreamListener listener);

    /** KB 派生索引同步（同 sourceId 覆盖）；检索未配置时抛 EXTERNAL_DEPENDENCY_BLOCKED。 */
    void upsertRetrievalDocument(AgentRetrievalDocument document);

    /**
     * 重嵌前探针。维度不一致且未确认重建时抛 {@link AgentDimensionChangeRequiredException}，
     * 携带目标维度与当前列维度。
     */
    AgentReembedPrepareResult prepareReembed(AgentModelSelection selection, boolean confirmRecreate);

    /** 从派生索引移除文档。 */
    void deleteRetrievalDocument(String siteId, String sourceId);

    /** 知识检索。检索未配置时抛 BusinessException（EXTERNAL_DEPENDENCY_BLOCKED，与 upsert 相同）。 */
    AgentRetrievalSearchResult searchRetrieval(AgentRetrievalSearchRequest request);

    /**
     * 扫描版 PDF 转写（模型原生文档理解）。模型链未配置或全部失败时抛
     * BusinessException（EXTERNAL_DEPENDENCY_BLOCKED）；调用方决定是否降级。
     */
    AgentOcrPdfResult ocrPdf(AgentOcrPdfRequest request);
}
