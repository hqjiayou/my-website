package com.mywebsite.agent.client;

import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentExperienceExtractRequest;
import com.mywebsite.agent.dto.AgentExperienceExtractResult;
import com.mywebsite.agent.dto.AgentMcpReady;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.dto.AgentOcrImageRequest;
import com.mywebsite.agent.dto.AgentOcrPdfRequest;
import com.mywebsite.agent.dto.AgentOcrPdfResult;
import com.mywebsite.agent.dto.AgentPingResponse;
import com.mywebsite.agent.dto.AgentReembedPrepareResult;
import com.mywebsite.agent.dto.AgentRetrievalDocument;
import com.mywebsite.agent.dto.AgentRetrievalSearchRequest;
import com.mywebsite.agent.dto.AgentRetrievalSearchResult;
import com.mywebsite.agent.dto.AgentRuntimeConfigPayload;
import com.mywebsite.agent.dto.AgentRuntimeConfigPushResult;
import com.mywebsite.agent.dto.AgentRuntimeStatus;
import com.mywebsite.agent.dto.AgentSkillSummary;
import java.util.List;

public interface AgentClient {

    AgentPingResponse ping();

    /** 运行时快照（Provider / Fallback / MCP / 预算）；不可达时抛 BusinessException。 */
    AgentRuntimeStatus runtimeStatus();

    /**
     * MCP 深检（全局启用集）。进程可达时返回探测明细（含 503 的 servers）；连不上 agent-services 才抛 BusinessException。
     */
    AgentMcpReady mcpReady();

    /**
     * 按名字限制的 MCP 深检。{@code onlyNames} 为空列表时不探任何 server；
     * 非空时只探这些名字与全局启用集的交集。
     */
    AgentMcpReady mcpReady(List<String> onlyNames);

    AgentChatResponse chat(AgentChatRequest request);

    /** agent-services 技能目录（平台能力，与站点无关）。 */
    List<AgentSkillSummary> listSkills();

    /**
     * 流式 chat：转发 agent-services 的应用层 SSE 事件。
     * 连接前校验失败抛 BusinessException；流中错误以 ERROR 事件送达 listener。
     */
    void chatStream(AgentChatRequest request, AgentStreamListener listener);

    /** KB 派生索引同步（同 sourceId 覆盖）；检索未配置时抛 RETRIEVAL_NOT_CONFIGURED，message 列出缺的键。 */
    void upsertRetrievalDocument(AgentRetrievalDocument document);

    /**
     * 重嵌前探针。维度不一致且未确认重建时抛 {@link AgentDimensionChangeRequiredException}，
     * 携带目标维度与当前列维度。
     */
    AgentReembedPrepareResult prepareReembed(AgentModelSelection selection, boolean confirmRecreate);

    /** 从派生索引移除文档。 */
    void deleteRetrievalDocument(String agentId, String sourceId);

    /** 知识检索。检索未配置时抛 BusinessException（EXTERNAL_DEPENDENCY_BLOCKED，与 upsert 相同）。 */
    AgentRetrievalSearchResult searchRetrieval(AgentRetrievalSearchRequest request);

    /**
     * 扫描版 PDF 转写（模型原生文档理解）。模型链未配置或全部失败时抛
     * BusinessException（EXTERNAL_DEPENDENCY_BLOCKED）；调用方决定是否降级。
     */
    AgentOcrPdfResult ocrPdf(AgentOcrPdfRequest request);

    /**
     * 图片转写（知识库图片上传，模型原生视觉理解）。响应与 {@link #ocrPdf} 同形状，
     * 复用 AgentOcrPdfResult 承载。失败语义同 ocrPdf。
     */
    AgentOcrPdfResult ocrImage(AgentOcrImageRequest request);

    /**
     * 经验提炼（Issue #184 UoW-2）：对预筛出的信号轮跑一次独立提炼，返回结构化候选
     * 与用量。模型链未配置或提炼失败时抛 BusinessException（EXTERNAL_DEPENDENCY_BLOCKED），
     * 调用方按批次失败计；无可提炼时返回空候选列表，不是错误。
     */
    AgentExperienceExtractResult extractExperience(AgentExperienceExtractRequest request);

    /**
     * 推送运行配置四项到 agent-services 热替换（Issue #146 UoW-1）。
     * 与本接口其他方法不同：推送失败不抛异常，结果以
     * {@link AgentRuntimeConfigPushResult} 返回（APPLIED/BLOCKED/FAILED），
     * 由调用方落 last_push_* 状态列；载荷含密钥明文，实现不得写入日志。
     */
    AgentRuntimeConfigPushResult pushRuntimeConfig(AgentRuntimeConfigPayload payload);
}
