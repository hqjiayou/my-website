package com.mywebsite.site.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.site.testsupport.AbstractSiteSpringBootTest;
import com.mywebsite.site.testsupport.SiteTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SitePingSecurityMvcTest extends AbstractSiteSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingIsPermitAllWithoutBearerAndReportsSiteReadyOnly() throws Exception {
        mockMvc.perform(get("/api/site/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.message").value("site service ready"))
                .andExpect(jsonPath("$.data.agent").doesNotExist())
                .andExpect(jsonPath("$.data.agentProviderStatus").doesNotExist());
    }

    @Test
    void adminTokenRejectedOnAuthenticatedSiteEndpoint() throws Exception {
        RSAKey pk = SiteTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        String adminToken = SiteTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", 100L);

        mockMvc.perform(get("/api/site/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
