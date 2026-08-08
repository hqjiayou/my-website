package com.mywebsite.agent.dto;

public record AgentPingResponse(String message, String providerStatus) {

    public AgentPingResponse(String message) {
        this(message, null);
    }
}
