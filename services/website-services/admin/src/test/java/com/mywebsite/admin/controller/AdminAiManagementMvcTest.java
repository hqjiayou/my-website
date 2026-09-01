package com.mywebsite.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mywebsite.admin.entity.AdminAiConversationEntity;
import com.mywebsite.admin.entity.AdminAiMessageEntity;
import com.mywebsite.admin.mapper.AdminAiConversationMapper;
import com.mywebsite.admin.mapper.AdminAiMessageMapper;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.client.AgentStreamListener;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@Sql(
        scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminAiManagementMvcTest extends AbstractAdminSpringBootTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminAiConversationMapper conversationMapper;

    @Autowired
    private AdminAiMessageMapper messageMapper;

    @MockBean
    private AgentClient agentClient;

    private String tokenFor(long userId) throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        return AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", userId);
    }

    @Test
    void promptProfileGetEmptyThenUpsert() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token);

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/ai/prompt-profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.siteId").value((int) siteId));

        ObjectNode body = MAPPER.createObjectNode();
        body.put("systemPrompt", "You are a shop assistant.");
        body.put("temperature", 0.2);
        mockMvc.perform(put("/api/admin/sites/" + siteId + "/ai/prompt-profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemPrompt").value("You are a shop assistant."))
                .andExpect(jsonPath("$.data.temperature").value(0.2));
    }

    @Test
    void knowledgeCrudAndCrossSite404() throws Exception {
        String token = tokenFor(500L);
        long siteA = createSite(token);
        long siteB = createSite(token);

        ObjectNode create = MAPPER.createObjectNode();
        create.put("title", "Hours");
        create.put("content", "09:00-18:00");
        MvcResult created = mockMvc.perform(post("/api/admin/sites/" + siteA + "/ai/knowledge-documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        long docId =
                MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/admin/sites/" + siteB + "/ai/knowledge-documents/" + docId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void conversationListIsSiteScoped() throws Exception {
        String token = tokenFor(500L);
        long siteA = createSite(token);
        long siteB = createSite(token);
        long conversationId = insertConversation(siteA, "hours?");
        insertConversation(siteB, "other");

        mockMvc.perform(get("/api/admin/sites/" + siteA + "/ai/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("hours?"));

        mockMvc.perform(get("/api/admin/sites/" + siteA + "/ai/conversations/" + conversationId
                                + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void previewStreamForwardsAgentEvents() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token);
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("START", "{\"ok\":true}");
                    listener.onEvent("TEXT_DELTA", "{\"delta\":\"hi\"}");
                    listener.onEvent("DONE", "{\"reply\":\"hi\"}");
                    return null;
                })
                .when(agentClient)
                .chatStream(any(), any());

        ObjectNode body = MAPPER.createObjectNode();
        body.put("message", "hello");
        MvcResult mvcResult = mockMvc.perform(post("/api/admin/sites/" + siteId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:TEXT_DELTA")));
    }

    @Test
    void previewForbiddenWithoutPermission() throws Exception {
        String token = tokenFor(100L);
        long siteId = createSite(tokenFor(500L));
        ObjectNode body = MAPPER.createObjectNode();
        body.put("message", "hello");
        mockMvc.perform(post("/api/admin/sites/" + siteId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void previewMapsBlocked() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token);
        doThrow(new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED))
                .when(agentClient)
                .chatStream(any(), any());

        ObjectNode body = MAPPER.createObjectNode();
        body.put("message", "hello");
        MvcResult mvcResult = mockMvc.perform(post("/api/admin/sites/" + siteId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("EXTERNAL_DEPENDENCY_BLOCKED")));
    }

    private long createSite(String token) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("siteCode", "ai_" + Long.toHexString(System.nanoTime()));
        body.put("siteName", "AI Site");
        body.put("siteType", "CAKE");
        MvcResult created = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long insertConversation(long siteId, String title) {
        AdminAiConversationEntity conversation = new AdminAiConversationEntity();
        conversation.setSiteId(siteId);
        conversation.setSiteUserId(1L);
        conversation.setTitle(title);
        conversation.setLastMessageAt(OffsetDateTime.now(ZoneOffset.UTC));
        conversationMapper.insert(conversation);

        AdminAiMessageEntity user = new AdminAiMessageEntity();
        user.setConversationId(conversation.getId());
        user.setSiteId(siteId);
        user.setRole(AdminAiMessageEntity.ROLE_USER);
        user.setContent(title);
        messageMapper.insert(user);

        AdminAiMessageEntity assistant = new AdminAiMessageEntity();
        assistant.setConversationId(conversation.getId());
        assistant.setSiteId(siteId);
        assistant.setRole(AdminAiMessageEntity.ROLE_ASSISTANT);
        assistant.setContent("ok");
        messageMapper.insert(assistant);
        return conversation.getId();
    }
}
