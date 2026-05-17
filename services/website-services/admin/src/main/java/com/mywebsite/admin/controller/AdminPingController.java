package com.mywebsite.admin.controller;

import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@SecurityRequirement(name = "adminBearer")
public class AdminPingController {

    private final AgentClient agentClient;

    public AdminPingController(AgentClient agentClient) {
        this.agentClient = agentClient;
    }

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.success(Map.of(
                "message", "admin service ready",
                "agent", agentClient.ping()
        ));
    }
}
