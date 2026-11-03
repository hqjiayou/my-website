package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 经验提炼请求（POST /internal/v1/experience/extract，Issue #184 UoW-2）。
 * 模型入口链与 chat/ocr 同形状（主用 + 各自带凭证的备援）；turns 是 Java 信号
 * 预筛出的候选轮，turnId 为消息 id 的字符串形态，提炼候选原样回带以便落来源。
 * existingTitles 是已批准条目与待审候选的标题，执行层据此去重、Java 落库前再核一遍。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentExperienceExtractRequest(
        AgentModelSelection model,
        List<AgentModelSelection> fallbackModels,
        List<Turn> turns,
        List<String> existingTitles) {

    /** 一轮候选对话：用户问、助手答与 run_meta 的信号摘要（纯文本，执行层只读不存）。 */
    public record Turn(String turnId, String userMessage, String assistantReply, String metaSummary) {}
}
