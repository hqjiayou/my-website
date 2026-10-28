package com.mywebsite.agent.controller;

import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import com.mywebsite.agent.dto.AgentAuthMeResponseDto;
import com.mywebsite.agent.service.AgentAuthMeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/auth")
@Tag(name = "Agent · Auth Context")
public class AgentAuthMeController {

    private final AgentAuthMeService agentAuthMeService;

    public AgentAuthMeController(AgentAuthMeService agentAuthMeService) {
        this.agentAuthMeService = agentAuthMeService;
    }

    @GetMapping("/me")
    @Operation(summary = "当前前台用户上下文")
    public ResponseEntity<HttpMessageBody<AgentAuthMeResponseDto>> me() {
        AgentAuthMeResponseDto data = agentAuthMeService.loadCurrentAgentUserContext();
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }
}
