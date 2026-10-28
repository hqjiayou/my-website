package com.mywebsite.agent.controller;

import com.mywebsite.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentPingController {

    /**
     * 公开探活：只表示 agent 自身就绪，不调用 agent（避免无 internal token 时 503）。
     */
    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.success(Map.of("message", "agent service ready"));
    }
}
