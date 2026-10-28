package com.mywebsite.agent.service;

import com.mywebsite.agent.dto.AgentAuthMeResponseDto;

public interface AgentAuthMeService {

    AgentAuthMeResponseDto loadCurrentAgentUserContext();
}
