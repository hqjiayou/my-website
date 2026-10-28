package com.mywebsite.agent.client;

/**
 * agent-services SSE 应用层事件回调（AgentStreamEvent 契约：
 * START | TEXT_DELTA | THINKING | TOOL_START | TOOL_RESULT | DONE | ERROR）。
 * data 为原始 JSON 字符串，由调用方按需转发或解析。
 */
@FunctionalInterface
public interface AgentStreamListener {

    void onEvent(String event, String dataJson);
}
