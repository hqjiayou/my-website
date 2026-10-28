package com.mywebsite.agent.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.agent.testsupport.AbstractAgentSpringBootTest;
import com.mywebsite.agent.testsupport.AgentTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AgentPingSecurityMvcTest extends AbstractAgentSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingIsPermitAllWithoutBearerAndReportsAgentReadyOnly() throws Exception {
        mockMvc.perform(get("/api/agent/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.message").value("agent service ready"))
                .andExpect(jsonPath("$.data.agent").doesNotExist())
                .andExpect(jsonPath("$.data.agentProviderStatus").doesNotExist());
    }

    @Test
    void adminTokenRejectedOnAuthenticatedAgentEndpoint() throws Exception {
        RSAKey pk = AgentTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        String adminToken = AgentTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", 100L);

        mockMvc.perform(get("/api/agent/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
