package com.mywebsite.agent.dto;

/**
 * {@code POST /internal/v1/retrieval/reembed/prepare}。
 * {@code model} 与 upsert 使用的入口凭证同一形状；为空时 Python 回退进程 embedding。
 */
public record AgentReembedPrepareRequest(AgentModelSelection model, boolean confirmRecreate) {}
