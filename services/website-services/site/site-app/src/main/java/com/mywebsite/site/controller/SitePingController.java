package com.mywebsite.site.controller;

import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/site")
public class SitePingController {

    private final AgentClient agentClient;

    public SitePingController(AgentClient agentClient) {
        this.agentClient = agentClient;
    }

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.success(Map.of(
                "message", "site service ready",
                "agent", agentClient.ping()
        ));
    }
}
