package com.mywebsite.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mywebsite.admin.entity.AdminAiConversationEntity;
import com.mywebsite.admin.entity.AdminAiMessageEntity;
import com.mywebsite.admin.entity.AdminAiPromptProfileEntity;
import com.mywebsite.admin.mapper.AdminAiConversationMapper;
import com.mywebsite.admin.mapper.AdminAiKnowledgeDocumentMapper;
import com.mywebsite.admin.mapper.AdminAiMessageMapper;
import com.mywebsite.admin.mapper.AdminAiPromptProfileMapper;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.client.AgentStreamListener;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentMcpReady;
import com.mywebsite.agent.dto.AgentRuntimeStatus;
import com.mywebsite.agent.dto.AgentSkillSummary;
import com.mywebsite.agent.dto.AgentTodoItem;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    @Autowired
    private AdminAiKnowledgeDocumentMapper knowledgeDocumentMapper;

    @Autowired
    private AdminAiPromptProfileMapper promptProfileMapper;

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
    void deleteSiteRemovesAiRowsAndRetrievalIndex() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token);

        ObjectNode prompt = MAPPER.createObjectNode();
        prompt.put("systemPrompt", "shop");
        prompt.put("temperature", 0.2);
        mockMvc.perform(put("/api/admin/sites/" + siteId + "/ai/prompt-profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(prompt)))
                .andExpect(status().isOk());

        ObjectNode knowledge = MAPPER.createObjectNode();
        knowledge.put("title", "Hours");
        knowledge.put("content", "09:00-18:00");
        MvcResult created = mockMvc.perform(post("/api/admin/sites/" + siteId + "/ai/knowledge-documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(knowledge)))
                .andExpect(status().isOk())
                .andReturn();
        long docId = MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        long conversationId = insertConversation(siteId, "hours?");

        doThrow(new RuntimeException("index down")).when(agentClient).deleteRetrievalDocument(any(), any());

        mockMvc.perform(delete("/api/admin/sites/" + siteId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(knowledgeDocumentMapper.selectById(docId)).isNull();
        assertThat(promptProfileMapper.selectCount(new LambdaQueryWrapper<AdminAiPromptProfileEntity>()
                        .eq(AdminAiPromptProfileEntity::getSiteId, siteId)))
                .isZero();
        assertThat(conversationMapper.selectById(conversationId)).isNull();
        assertThat(messageMapper.selectCount(new LambdaQueryWrapper<AdminAiMessageEntity>()
                        .eq(AdminAiMessageEntity::getConversationId, conversationId)))
                .isZero();
        verify(agentClient).deleteRetrievalDocument(String.valueOf(siteId), "kb:" + docId);
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
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].agentMeta").doesNotExist())
                .andExpect(jsonPath("$.data[1].agentMeta.usage.toolCalls").value(1))
                .andExpect(jsonPath("$.data[1].agentMeta.tools[0].name").value("list_todos"))
                .andExpect(jsonPath("$.data[1].agentMeta.skillsApplied[0]").value("orchestration"));
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
    void previewForwardsSkillsAndTodosToAgent() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token);
        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("DONE", "{\"reply\":\"hi\"}");
                    return null;
                })
                .when(agentClient)
                .chatStream(captor.capture(), any());

        String body = "{\"message\":\"hello\",\"skills\":[\" orchestration \",\"orchestration\"],"
                + "\"todos\":[{\"id\":\"t1\",\"title\":\"Step 1\",\"status\":\"IN_PROGRESS\"}]}";
        MvcResult mvcResult = mockMvc.perform(post("/api/admin/sites/" + siteId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());

        AgentChatRequest sent = captor.getValue();
        assertThat(sent.locale()).isEqualTo("zh-CN");
        assertThat(sent.skills()).containsExactly("orchestration");
        assertThat(sent.todos()).containsExactly(new AgentTodoItem("t1", "Step 1", "IN_PROGRESS"));
    }

    @Test
    void previewRejectsInvalidTodoStatus() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token);
        String body = "{\"message\":\"hello\",\"todos\":[{\"id\":\"t1\",\"title\":\"x\",\"status\":\"DONE\"}]}";
        mockMvc.perform(post("/api/admin/sites/" + siteId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewSkillsCatalogIsProxied() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token);
        when(agentClient.listSkills()).thenReturn(List.of(new AgentSkillSummary("orchestration", "plan", "1")));

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/ai/preview/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("orchestration"))
                .andExpect(jsonPath("$.data[0].description").value("plan"));
    }

    @Test
    void runtimeStatusIsProxiedAndUnreachableIsReportedNotThrown() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token);
        when(agentClient.runtimeStatus()).thenReturn(new AgentRuntimeStatus(
                "openai:gpt-4o",
                "LIVE",
                List.of("anthropic:claude"),
                "CONFIGURED",
                List.of(new AgentRuntimeStatus.McpServer("echo", true)),
                new AgentRuntimeStatus.Limits(10, 20, null, 30.0, 24000),
                "BLOCKED",
                "Retrieval database is not configured (missing RETRIEVAL_DATABASE_URL)."));

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/ai/runtime")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reachable").value(true))
                .andExpect(jsonPath("$.data.status.providerStatus").value("LIVE"))
                .andExpect(jsonPath("$.data.status.fallbackProviders[0]").value("anthropic:claude"))
                .andExpect(jsonPath("$.data.status.mcpServers[0].name").value("echo"))
                .andExpect(jsonPath("$.data.status.limits.maxRequests").value(10))
                .andExpect(jsonPath("$.data.status.retrievalStatus").value("BLOCKED"));

        when(agentClient.runtimeStatus()).thenThrow(new BusinessException(ErrorCode.SYSTEM_ERROR));
        mockMvc.perform(get("/api/admin/sites/" + siteId + "/ai/runtime")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reachable").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("SYSTEM_ERROR"));

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/ai/runtime")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(100L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void mcpReadyIsProxiedIncludingUnreachableProbe() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token);
        when(agentClient.mcpReady())
                .thenReturn(new AgentMcpReady(
                        false,
                        "CONFIGURED",
                        List.of(new AgentMcpReady.Server("dead", false, null, "TimeoutError")),
                        "MCP_UNAVAILABLE",
                        "MCP server unreachable: dead"));

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/ai/mcp-ready")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reachable").value(true))
                .andExpect(jsonPath("$.data.probe.ok").value(false))
                .andExpect(jsonPath("$.data.probe.servers[0].name").value("dead"))
                .andExpect(jsonPath("$.data.probe.servers[0].error").value("TimeoutError"));

        when(agentClient.mcpReady()).thenThrow(new BusinessException(ErrorCode.SYSTEM_ERROR));
        mockMvc.perform(get("/api/admin/sites/" + siteId + "/ai/mcp-ready")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reachable").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("SYSTEM_ERROR"));
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
        assistant.setAgentMeta("{\"usage\":{\"requests\":2,\"toolCalls\":1,\"inputTokens\":30,\"outputTokens\":5},"
                + "\"tools\":[{\"name\":\"list_todos\",\"ok\":true}],\"skillsApplied\":[\"orchestration\"],\"x\":1}");
        messageMapper.insert(assistant);
        return conversation.getId();
    }
}
