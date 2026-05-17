package com.mywebsite.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.common.security.JwtClaimConstants;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdminPingSecurityMvcTest extends AbstractAdminSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentClient agentClient;

    @Test
    void pingWithoutBearerIs401() throws Exception {
        mockMvc.perform(get("/api/admin/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void pingWithSyntheticJwtSucceeds() throws Exception {
        org.mockito.Mockito.when(agentClient.ping()).thenReturn("ok");

        mockMvc.perform(get("/api/admin/ping")
                        .with(jwt()
                                .jwt(j -> j.claim(JwtClaimConstants.ADMIN_USER_ID, 100L)
                                        .claim(JwtClaimConstants.USERNAME, "admin_it"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void pingWithValidRs256BearerToken() throws Exception {
        org.mockito.Mockito.when(agentClient.ping()).thenReturn("ok");
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        String token = AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN");

        mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void rs256SignedWithWrongKeyRejected() throws Exception {
        RSAKey wrong = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/wrong-private.pem");
        String token = AdminTestJwtSigner.signAdminAccess(wrong, "test-issuer", "ADMIN");
        mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        result -> assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                                .contains("invalid_token"));
    }

    @Test
    void tamperedAccessTokenRejected() throws Exception {
        String garbage = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.invalid-signature";
        mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + garbage))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        result -> assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                                .contains("invalid_token"));
    }
}
