package com.mywebsite.site.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.site.testsupport.AbstractSiteSpringBootTest;
import com.mywebsite.site.testsupport.SiteTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SiteAiChatMvcTest extends AbstractSiteSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    private RSAKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        privateKey = SiteTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
    }

    @Test
    void chatWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/site/10/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatReturnsExternalDependencyBlockedWhenProviderHasNoKey() throws Exception {
        String token = SiteTestJwtSigner.signSiteAccess(privateKey, "test-issuer", "SITE", 200L, 10L);

        mockMvc.perform(post("/api/site/10/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode()))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void chatRejectsMismatchedSiteId() throws Exception {
        String token = SiteTestJwtSigner.signSiteAccess(privateKey, "test-issuer", "SITE", 200L, 10L);

        mockMvc.perform(post("/api/site/999/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }
}
