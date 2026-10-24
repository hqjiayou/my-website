package com.mywebsite.site.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.site.service.SiteAiConversationService;
import com.mywebsite.site.service.SiteAiConversationService.ChatPreparation;
import com.mywebsite.site.testsupport.AbstractSiteSpringBootTest;
import com.mywebsite.site.testsupport.SiteTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SiteAiChatMvcTest extends AbstractSiteSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SiteAiConversationService conversationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 仅预算用例覆盖；其余用例仍走 AbstractSiteSpringBootTest 的 agent stub。 */
    @MockBean
    private AgentClient agentClient;

    private RSAKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        privateKey = SiteTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        // doThrow：避免 when().thenThrow 在重设 stub 时触发旧异常
        doThrow(new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED))
                .when(agentClient)
                .chat(any());
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
    void chatReturnsAgentBudgetExceededAsUnprocessableEntity() throws Exception {
        doThrow(new BusinessException(ErrorCode.AGENT_BUDGET_EXCEEDED)).when(agentClient).chat(any());
        String token = SiteTestJwtSigner.signSiteAccess(privateKey, "test-issuer", "SITE", 200L, 10L);

        mockMvc.perform(post("/api/site/10/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.AGENT_BUDGET_EXCEEDED.getCode()))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void chatRejectsMessageLongerThanAgentCap() throws Exception {
        String token = SiteTestJwtSigner.signSiteAccess(privateKey, "test-issuer", "SITE", 200L, 10L);
        String message = "a".repeat(16_001);

        mockMvc.perform(post("/api/site/10/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
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

    @Test
    void renameConversationRouteTrimsTitle() throws Exception {
        ChatPreparation prepared = conversationService.prepare(10L, 200L, null);
        conversationService.recordExchange(prepared.conversation(), "hello", "ok", List.of(), null);
        long id = prepared.conversation().getId();
        String token = SiteTestJwtSigner.signSiteAccess(privateKey, "test-issuer", "SITE", 200L, 10L);

        mockMvc.perform(patch("/api/site/10/ai/conversations/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  renamed  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("renamed"))
                .andExpect(jsonPath("$.data.id").value(id));

        assertEquals(
                "renamed",
                jdbcTemplate.queryForObject("SELECT title FROM site_ai_conversation WHERE id = ?", String.class, id));
    }

    @Test
    void deleteConversationRouteRemovesMessagesAndConversation() throws Exception {
        ChatPreparation prepared = conversationService.prepare(10L, 200L, null);
        conversationService.recordExchange(prepared.conversation(), "hello", "ok", List.of(), null);
        long id = prepared.conversation().getId();
        String token = SiteTestJwtSigner.signSiteAccess(privateKey, "test-issuer", "SITE", 200L, 10L);

        mockMvc.perform(delete("/api/site/10/ai/conversations/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));

        Number conversations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM site_ai_conversation WHERE id = ?", Number.class, id);
        Number messages = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM site_ai_message WHERE conversation_id = ?", Number.class, id);
        assertEquals(0L, conversations.longValue());
        assertEquals(0L, messages.longValue());
    }
}
