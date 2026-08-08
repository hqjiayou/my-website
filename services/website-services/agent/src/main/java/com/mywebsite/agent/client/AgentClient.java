package com.mywebsite.agent.client;

import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentPingResponse;

public interface AgentClient {

    AgentPingResponse ping();

    AgentChatResponse chat(AgentChatRequest request);
}
