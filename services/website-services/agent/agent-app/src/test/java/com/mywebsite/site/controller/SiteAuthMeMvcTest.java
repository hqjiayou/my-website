package com.mywebsite.site.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.site.entity.SiteUserEntity;
import com.mywebsite.site.entity.SiteUserProfileEntity;
import com.mywebsite.site.mapper.SiteUserMapper;
import com.mywebsite.site.mapper.SiteUserProfileMapper;
import com.mywebsite.site.testsupport.AbstractSiteSpringBootTest;
import com.mywebsite.site.testsupport.SiteTestJwtSigner;
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
class SiteAuthMeMvcTest extends AbstractSiteSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SiteUserMapper siteUserMapper;

    @MockBean
    private SiteUserProfileMapper siteUserProfileMapper;

    private RSAKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        privateKey = SiteTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");

        SiteUserEntity user = new SiteUserEntity();
        user.setId(200L);
        user.setSiteId(10L);
        user.setUsername("site_it");
        user.setStatus("ACTIVE");
        Mockito.when(siteUserMapper.selectById(200L)).thenReturn(user);

        SiteUserProfileEntity profile = new SiteUserProfileEntity();
        profile.setSiteUserId(200L);
        profile.setSiteId(10L);
        profile.setDisplayName("Site IT");
        profile.setAvatarUrl("https://example.com/a.png");
        Mockito.when(siteUserProfileMapper.selectOne(any())).thenReturn(profile);
    }

    @Test
    void meReturnsRealUserDataWithEmptyRolesPermissionsMenus() throws Exception {
        String token = SiteTestJwtSigner.signSiteAccess(privateKey, "test-issuer", "SITE", 200L, 10L);

        mockMvc.perform(get("/api/site/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.user.userCode").value(SubjectCodes.formatSiteUserSubject(200L)))
                .andExpect(jsonPath("$.data.user.username").value("site_it"))
                .andExpect(jsonPath("$.data.user.displayName").value("Site IT"))
                .andExpect(jsonPath("$.data.user.avatarUrl").value("https://example.com/a.png"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.roles").isEmpty())
                .andExpect(jsonPath("$.data.permissions").isEmpty())
                .andExpect(jsonPath("$.data.menus").isEmpty());
    }

    @Test
    void meRejectsWhenJwtSiteIdDoesNotMatchUser() throws Exception {
        String token = SiteTestJwtSigner.signSiteAccess(privateKey, "test-issuer", "SITE", 200L, 999L);

        mockMvc.perform(get("/api/site/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void meRejectsWhenUserIsNotActive() throws Exception {
        SiteUserEntity disabled = new SiteUserEntity();
        disabled.setId(200L);
        disabled.setSiteId(10L);
        disabled.setUsername("site_it");
        disabled.setStatus("DISABLED");
        Mockito.when(siteUserMapper.selectById(200L)).thenReturn(disabled);

        String token = SiteTestJwtSigner.signSiteAccess(privateKey, "test-issuer", "SITE", 200L, 10L);

        mockMvc.perform(get("/api/site/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void adminAudienceTokenIsRejectedOnSiteMe() throws Exception {
        // ADMIN aud + subject_type → Site RS 拒绝（跨 audience）
        String token = SiteTestJwtSigner.signAdminAccess(privateKey, "test-issuer", "ADMIN", 100L);

        mockMvc.perform(get("/api/site/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
