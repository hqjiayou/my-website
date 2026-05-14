package com.mywebsite.agent.client;

import org.springframework.stereotype.Component;

@Component
public class DefaultAgentClient implements AgentClient {

    @Override
    public String ping() {
        return "agent module ready";
    }
}
