package com.mywebsite.agent.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.agent.entity.AgentUserEntity;
import com.mywebsite.agent.entity.AgentUserProfileEntity;
import com.mywebsite.agent.mapper.AgentUserMapper;
import com.mywebsite.agent.mapper.AgentUserProfileMapper;
import com.mywebsite.agent.testsupport.AbstractAgentSpringBootTest;
import com.mywebsite.agent.testsupport.AgentTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AgentAuthMeMvcTest extends AbstractAgentSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentUserMapper agentUserMapper;

    @MockBean
    private AgentUserProfileMapper agentUserProfileMapper;

    private RSAKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        privateKey = AgentTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");

        AgentUserEntity user = new AgentUserEntity();
        user.setId(200L);
        user.setAgentId(10L);
        user.setUsername("site_it");
        user.setStatus("ACTIVE");
        Mockito.when(agentUserMapper.selectById(200L)).thenReturn(user);

        AgentUserProfileEntity profile = new AgentUserProfileEntity();
        profile.setAgentUserId(200L);
        profile.setAgentId(10L);
        profile.setDisplayName("Agent IT");
        profile.setAvatarUrl("https://example.com/a.png");
        Mockito.when(agentUserProfileMapper.selectOne(any())).thenReturn(profile);
    }

    @Test
    void meReturnsRealUserDataWithEmptyRolesPermissionsMenus() throws Exception {
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);

        mockMvc.perform(get("/api/agent/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.user.userCode").value(SubjectCodes.formatAgentUserSubject(200L)))
                .andExpect(jsonPath("$.data.user.username").value("site_it"))
                .andExpect(jsonPath("$.data.user.displayName").value("Agent IT"))
                .andExpect(jsonPath("$.data.user.avatarUrl").value("https://example.com/a.png"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.roles").isEmpty())
                .andExpect(jsonPath("$.data.permissions").isEmpty())
                .andExpect(jsonPath("$.data.menus").isEmpty());
    }

    @Test
    void meRejectsWhenJwtAgentIdDoesNotMatchUser() throws Exception {
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 999L);

        mockMvc.perform(get("/api/agent/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void meRejectsWhenUserIsNotActive() throws Exception {
        AgentUserEntity disabled = new AgentUserEntity();
        disabled.setId(200L);
        disabled.setAgentId(10L);
        disabled.setUsername("site_it");
        disabled.setStatus("DISABLED");
        Mockito.when(agentUserMapper.selectById(200L)).thenReturn(disabled);

        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);

        mockMvc.perform(get("/api/agent/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void adminAudienceTokenIsRejectedOnAgentMe() throws Exception {
        // ADMIN aud + subject_type → Agent RS 拒绝（跨 audience）
        String token = AgentTestJwtSigner.signAdminAccess(privateKey, "test-issuer", "ADMIN", 100L);

        mockMvc.perform(get("/api/agent/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
