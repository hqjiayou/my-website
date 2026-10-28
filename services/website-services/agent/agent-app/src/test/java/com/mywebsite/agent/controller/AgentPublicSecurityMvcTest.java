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
        mockMvc.perform(get("/api/agent/public/missing-public-agent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));
        mockMvc.perform(get("/api/agent/public/missing-public-agent/contents"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));
        mockMvc.perform(get("/api/agent/public/missing-public-agent/contents/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void publicPostIsNotPermitAll() throws Exception {
        mockMvc.perform(post("/api/agent/public/missing-public-agent"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }
}
