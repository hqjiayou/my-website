package com.mywebsite.agent.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.agent.testsupport.AbstractAgentSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** 匿名可读公开 GET；其他方法不放行。架子与 {@link AgentPingSecurityMvcTest} 相同。 */
@AutoConfigureMockMvc
class AgentPublicSecurityMvcTest extends AbstractAgentSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousCanReadPublicGets() throws Exception {
        // 公开面只剩分享读：匿名 GET 凭 token 读分享，未知 token 是业务 NOT_FOUND（而非 401），证明 permitAll 生效。
        mockMvc.perform(get("/api/agent/public/ai/shares/nonexistent-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void publicPostIsNotPermitAll() throws Exception {
        mockMvc.perform(post("/api/agent/public/ai/shares/nonexistent-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }
}
