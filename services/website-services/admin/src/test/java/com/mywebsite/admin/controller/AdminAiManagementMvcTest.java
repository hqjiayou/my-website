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
import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.entity.AdminAiPromptProfileEntity;
import com.mywebsite.admin.mapper.AdminAiConversationMapper;
import com.mywebsite.admin.mapper.AdminAiKnowledgeDocumentMapper;
import com.mywebsite.admin.mapper.AdminAiMessageMapper;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.admin.mapper.AdminAiPromptProfileMapper;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.client.AgentStreamListener;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentExperienceLessonItem;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private AdminAiModelEntryMapper modelEntryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AgentClient agentClient;

    /**
     * 本类会创建全站（agentId 为空）模型入口，而 @Sql 的 rbac-h2-data.sql 只清
     * RBAC/agent 等表、不清 agent_model_entry，入口会跨方法残留并污染后续方法
     * （缺省模式判定、全站默认回退、runtime overlay 都读全站入口，见 Issue #163）。
     * 每个方法前清干净，保证方法间隔离。
     */
    @BeforeEach
    void clearModelEntries() {
        modelEntryMapper.delete(new LambdaQueryWrapper<AdminAiModelEntryEntity>());
    }

    private String tokenFor(long userId) throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        return AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", userId);
    }

    @Test
    void promptProfileGetEmptyThenUpsert() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);

        mockMvc.perform(get("/api/admin/agents/" + agentId + "/ai/prompt-profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.agentId").value((int) agentId));

        ObjectNode body = MAPPER.createObjectNode();
        body.put("systemPrompt", "You are a shop assistant.");
        body.put("temperature", 0.2);
        mockMvc.perform(put("/api/admin/agents/" + agentId + "/ai/prompt-profile")
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
        long agentA = createSite(token);
        long agentB = createSite(token);

        ObjectNode create = MAPPER.createObjectNode();
        create.put("title", "Hours");
        create.put("content", "09:00-18:00");
        MvcResult created = mockMvc.perform(post("/api/admin/agents/" + agentA + "/ai/knowledge-documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        long docId =
                MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/admin/agents/" + agentB + "/ai/knowledge-documents/" + docId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAgentRemovesAiRowsAndRetrievalIndex() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);

        ObjectNode prompt = MAPPER.createObjectNode();
        prompt.put("systemPrompt", "shop");
        prompt.put("temperature", 0.2);
        mockMvc.perform(put("/api/admin/agents/" + agentId + "/ai/prompt-profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(prompt)))
                .andExpect(status().isOk());

        ObjectNode knowledge = MAPPER.createObjectNode();
        knowledge.put("title", "Hours");
        knowledge.put("content", "09:00-18:00");
        MvcResult created = mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/knowledge-documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(knowledge)))
                .andExpect(status().isOk())
                .andReturn();
        long docId = MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        long conversationId = insertConversation(agentId, "hours?");

        doThrow(new RuntimeException("index down")).when(agentClient).deleteRetrievalDocument(any(), any());

        mockMvc.perform(delete("/api/admin/agents/" + agentId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(knowledgeDocumentMapper.selectById(docId)).isNull();
        assertThat(promptProfileMapper.selectCount(new LambdaQueryWrapper<AdminAiPromptProfileEntity>()
                        .eq(AdminAiPromptProfileEntity::getAgentId, agentId)))
                .isZero();
        assertThat(conversationMapper.selectById(conversationId)).isNull();
        assertThat(messageMapper.selectCount(new LambdaQueryWrapper<AdminAiMessageEntity>()
                        .eq(AdminAiMessageEntity::getConversationId, conversationId)))
                .isZero();
        verify(agentClient).deleteRetrievalDocument(String.valueOf(agentId), "kb:" + docId);
    }

    @Test
    void conversationListIsAgentScoped() throws Exception {
        String token = tokenFor(500L);
        long agentA = createSite(token);
        long agentB = createSite(token);
        long conversationId = insertConversation(agentA, "hours?");
        insertConversation(agentB, "other");

        mockMvc.perform(get("/api/admin/agents/" + agentA + "/ai/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("hours?"));

        mockMvc.perform(get("/api/admin/agents/" + agentA + "/ai/conversations/" + conversationId
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
        long agentId = createSite(token);
        // 自备站点 CLI 默认入口：链为空会诚实 BLOCKED、到不了 agentClient mock；
        // 本类每方法前清空入口表（见 clearModelEntries），不能再靠其他方法的残留。
        ObjectNode cliEntry = MAPPER.createObjectNode();
        cliEntry.put("displayName", "CLI default");
        cliEntry.put("modelSpec", "openai:muse-wonder-1.0");
        cliEntry.put("apiKey", "client-key");
        cliEntry.put("agentId", agentId);
        cliEntry.put("mode", "CLI");
        cliEntry.put("enabled", true);
        cliEntry.put("defaultEntry", true);
        mockMvc.perform(post("/api/admin/ai/model-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(cliEntry)))
                .andExpect(status().isOk());
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
        MvcResult mvcResult = mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
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
    void previewStreamForwardsVisualizationEventAndMarksPreview() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        // 同 previewStreamForwardsAgentEvents：自备站点 CLI 默认入口，保证链非空。
        ObjectNode cliEntry = MAPPER.createObjectNode();
        cliEntry.put("displayName", "CLI default");
        cliEntry.put("modelSpec", "openai:muse-wonder-1.0");
        cliEntry.put("apiKey", "client-key");
        cliEntry.put("agentId", agentId);
        cliEntry.put("mode", "CLI");
        cliEntry.put("enabled", true);
        cliEntry.put("defaultEntry", true);
        mockMvc.perform(post("/api/admin/ai/model-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(cliEntry)))
                .andExpect(status().isOk());
        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        String cardJson = "{\"kind\":\"weather\",\"payload\":{\"current\":{\"temperatureC\":12.3}}}";
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("START", "{}");
                    listener.onEvent("VISUALIZATION", cardJson);
                    listener.onEvent("DONE", "{\"reply\":\"hi\"}");
                    return null;
                })
                .when(agentClient)
                .chatStream(captor.capture(), any());

        ObjectNode body = MAPPER.createObjectNode();
        body.put("message", "weather?");
        MvcResult mvcResult = mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // VISUALIZATION 与 DONE 的 dataJson 透传口径一致：事件名与信封原文到达前端 SSE。
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:VISUALIZATION")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(cardJson)));
        // 试聊标记随请求下发执行层（Issue #179 卡片门控）。
        assertThat(captor.getValue().preview()).isTrue();
    }

    @Test
    void previewInjectsApprovedExperienceLessons() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        // 自备站点 CLI 默认入口，保证链非空（同 previewStreamForwardsAgentEvents）。
        ObjectNode cliEntry = MAPPER.createObjectNode();
        cliEntry.put("displayName", "CLI default");
        cliEntry.put("modelSpec", "openai:muse-wonder-1.0");
        cliEntry.put("apiKey", "client-key");
        cliEntry.put("agentId", agentId);
        cliEntry.put("mode", "CLI");
        cliEntry.put("enabled", true);
        cliEntry.put("defaultEntry", true);
        mockMvc.perform(post("/api/admin/ai/model-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(cliEntry)))
                .andExpect(status().isOk());
        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("START", "{}");
                    listener.onEvent("DONE", "{\"reply\":\"hi\"}");
                    return null;
                })
                .when(agentClient)
                .chatStream(captor.capture(), any());

        // 无已批准条目时不带字段（Issue #184 UoW-4）：先清空条目表保确定性。
        jdbcTemplate.execute("DELETE FROM agent_experience_lesson");
        ObjectNode body = MAPPER.createObjectNode();
        body.put("message", "hello");
        MvcResult first = mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(first)).andExpect(status().isOk());
        assertThat(captor.getValue().experienceLessons()).isNull();

        // 经人审端点新建并批准一条（L6），试聊下一轮即带上（请求注入、批准即生效）。
        MvcResult created = mockMvc.perform(post("/api/admin/ai/experience-lessons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"集合问题先枚举\",\"symptom\":\"症状\","
                                + "\"lesson\":\"先枚举再逐份抽取\",\"evidence\":\"证据\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long lessonId = MAPPER.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(post("/api/admin/ai/experience-lessons/" + lessonId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        MvcResult second = mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(second)).andExpect(status().isOk());
        assertThat(captor.getValue().experienceLessons())
                .containsExactly(new AgentExperienceLessonItem(6, "先枚举再逐份抽取"));
    }

    @Test
    void previewForwardsSkillsAndTodosToAgent() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        // 同 previewStreamForwardsAgentEvents：自备站点 CLI 默认入口，保证链非空。
        ObjectNode cliEntry = MAPPER.createObjectNode();
        cliEntry.put("displayName", "CLI default");
        cliEntry.put("modelSpec", "openai:muse-wonder-1.0");
        cliEntry.put("apiKey", "client-key");
        cliEntry.put("agentId", agentId);
        cliEntry.put("mode", "CLI");
        cliEntry.put("enabled", true);
        cliEntry.put("defaultEntry", true);
        mockMvc.perform(post("/api/admin/ai/model-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(cliEntry)))
                .andExpect(status().isOk());
        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("DONE", "{\"reply\":\"hi\"}");
                    return null;
                })
                .when(agentClient)
                .chatStream(captor.capture(), any());

        String body = "{\"message\":\"hello\",\"skills\":[\" orchestration \",\"orchestration\"],"
                + "\"todos\":[{\"id\":\"t1\",\"title\":\"Step 1\",\"status\":\"IN_PROGRESS\"}],"
                + "\"timezone\":\" Asia/Tokyo \",\"latitude\":35.68124,\"longitude\":139.76712,\"accuracyMeters\":18}";
        MvcResult mvcResult = mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());

        AgentChatRequest sent = captor.getValue();
        assertThat(sent.locale()).isEqualTo("zh-CN");
        assertThat(sent.timezone()).isEqualTo("Asia/Tokyo");
        assertThat(sent.latitude()).isEqualTo(35.68124);
        assertThat(sent.longitude()).isEqualTo(139.76712);
        assertThat(sent.accuracyMeters()).isEqualTo(18.0);
        assertThat(sent.skills()).containsExactly("orchestration");
        assertThat(sent.todos()).containsExactly(new AgentTodoItem("t1", "Step 1", "IN_PROGRESS"));
    }

    @Test
    void previewEmptySkillsStayEmptyAndDoesNotSendFallbacks() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        // 先给站点配一个 CLI 默认入口：链为空已改为诚实 BLOCKED（见 previewStreamBlocksWhenModeChainEmpty），
        // 本用例关注的是 skills=[] 原样透传、不发进程默认 skills，与模型选择无关。
        ObjectNode cliEntry = MAPPER.createObjectNode();
        cliEntry.put("displayName", "CLI default");
        cliEntry.put("modelSpec", "openai:muse-wonder-1.0");
        cliEntry.put("apiKey", "client-key");
        cliEntry.put("agentId", agentId);
        cliEntry.put("mode", "CLI");
        cliEntry.put("enabled", true);
        cliEntry.put("defaultEntry", true);
        mockMvc.perform(post("/api/admin/ai/model-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(cliEntry)))
                .andExpect(status().isOk());
        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("DONE", "{\"reply\":\"hi\"}");
                    return null;
                })
                .when(agentClient)
                .chatStream(captor.capture(), any());

        String body = "{\"message\":\"hello\",\"skills\":[],\"mode\":\"CLI\"}";
        MvcResult mvcResult = mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());

        AgentChatRequest sent = captor.getValue();
        assertThat(sent.skills()).isEmpty();
        assertThat(sent.timezone()).isNull();
        assertThat(sent.latitude()).isNull();
        assertThat(sent.longitude()).isNull();
        assertThat(sent.fallbackModels()).isNull();
        assertThat(sent.model().spec()).isEqualTo("openai:muse-wonder-1.0");
    }

    @Test
    void previewStreamBlocksWhenModeChainEmpty() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        // 站点无任何模型入口：所选模式链为空 → SSE ERROR 携带 EXTERNAL_DEPENDENCY_BLOCKED，
        // 且不向 agent-services 发起请求（不再以 model=null 落进程模型应答）。
        String body = "{\"message\":\"hello\",\"mode\":\"CLI\"}";
        MvcResult mvcResult = mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("EXTERNAL_DEPENDENCY_BLOCKED")));
        org.mockito.Mockito.verify(agentClient, org.mockito.Mockito.never()).chatStream(any(), any());
    }

    @Test
    void previewRejectsInvalidTodoStatus() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        String body = "{\"message\":\"hello\",\"todos\":[{\"id\":\"t1\",\"title\":\"x\",\"status\":\"DONE\"}]}";
        mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewSkillsCatalogIsProxied() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        when(agentClient.listSkills()).thenReturn(List.of(new AgentSkillSummary("orchestration", "plan", "1")));

        mockMvc.perform(get("/api/admin/agents/" + agentId + "/ai/preview/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("orchestration"))
                .andExpect(jsonPath("$.data[0].description").value("plan"));
    }

    @Test
    void runtimeStatusIsProxiedAndUnreachableIsReportedNotThrown() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        when(agentClient.runtimeStatus()).thenReturn(new AgentRuntimeStatus(
                "openai:gpt-4o",
                "LIVE",
                List.of("anthropic:claude"),
                "CONFIGURED",
                List.of(new AgentRuntimeStatus.McpServer("echo", true)),
                new AgentRuntimeStatus.Limits(10, 20, null, 30.0, 24000),
                "BLOCKED",
                "Retrieval database is not configured (missing RETRIEVAL_DATABASE_URL).",
                new AgentRuntimeStatus.HarnessFiles(true, true, false)));

        mockMvc.perform(get("/api/admin/agents/" + agentId + "/ai/runtime")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reachable").value(true))
                .andExpect(jsonPath("$.data.status.providerStatus").value("LIVE"))
                .andExpect(jsonPath("$.data.status.fallbackProviders[0]").value("anthropic:claude"))
                .andExpect(jsonPath("$.data.status.mcpServers[0].name").value("echo"))
                .andExpect(jsonPath("$.data.status.limits.maxRequests").value(10))
                .andExpect(jsonPath("$.data.status.retrievalStatus").value("BLOCKED"))
                .andExpect(jsonPath("$.data.status.harness.background").value(true))
                .andExpect(jsonPath("$.data.status.harness.guardrails").value(true))
                .andExpect(jsonPath("$.data.status.harness.toolGuidance").value(false));

        when(agentClient.runtimeStatus()).thenThrow(new BusinessException(ErrorCode.SYSTEM_ERROR));
        mockMvc.perform(get("/api/admin/agents/" + agentId + "/ai/runtime")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reachable").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("SYSTEM_ERROR"));

        mockMvc.perform(get("/api/admin/agents/" + agentId + "/ai/runtime")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(100L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void mcpReadyIsProxiedIncludingUnreachableProbe() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        when(agentClient.mcpReady())
                .thenReturn(new AgentMcpReady(
                        false,
                        "CONFIGURED",
                        List.of(new AgentMcpReady.Server("dead", false, null, "TimeoutError")),
                        "MCP_UNAVAILABLE",
                        "MCP server unreachable: dead"));

        mockMvc.perform(get("/api/admin/agents/" + agentId + "/ai/mcp-ready")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reachable").value(true))
                .andExpect(jsonPath("$.data.probe.ok").value(false))
                .andExpect(jsonPath("$.data.probe.servers[0].name").value("dead"))
                .andExpect(jsonPath("$.data.probe.servers[0].error").value("TimeoutError"));

        when(agentClient.mcpReady()).thenThrow(new BusinessException(ErrorCode.SYSTEM_ERROR));
        mockMvc.perform(get("/api/admin/agents/" + agentId + "/ai/mcp-ready")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reachable").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("SYSTEM_ERROR"));
    }

    @Test
    void previewForbiddenWithoutPermission() throws Exception {
        String token = tokenFor(100L);
        long agentId = createSite(tokenFor(500L));
        ObjectNode body = MAPPER.createObjectNode();
        body.put("message", "hello");
        mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void previewMapsBlocked() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        doThrow(new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED))
                .when(agentClient)
                .chatStream(any(), any());

        ObjectNode body = MAPPER.createObjectNode();
        body.put("message", "hello");
        MvcResult mvcResult = mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("EXTERNAL_DEPENDENCY_BLOCKED")));
    }

    @Test
    void previewModelBackfillMatchesSelectionChainJudgment() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);

        // 没有任何入口：mode 缺省按 previewMode 默认判定落到 KEY（CLI 链空），该模式也不可用。
        mockMvc.perform(get("/api/admin/agents/" + agentId + "/ai/preview/model")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("KEY"))
                .andExpect(jsonPath("$.data.modelSpec").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.available").value(false));

        ObjectNode cliEntry = MAPPER.createObjectNode();
        cliEntry.put("displayName", "CLI default");
        cliEntry.put("modelSpec", "openai:muse-wonder-1.0");
        cliEntry.put("apiKey", "client-key");
        cliEntry.put("agentId", agentId);
        cliEntry.put("mode", "CLI");
        cliEntry.put("enabled", true);
        cliEntry.put("defaultEntry", true);
        MvcResult cliCreated = mockMvc.perform(post("/api/admin/ai/model-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(cliEntry)))
                .andExpect(status().isOk())
                .andReturn();
        long cliId =
                MAPPER.readTree(cliCreated.getResponse().getContentAsString()).path("data").path("id").asLong();

        ObjectNode keyEntry = MAPPER.createObjectNode();
        keyEntry.put("displayName", "KEY default");
        keyEntry.put("modelSpec", "openai:gpt-4o-mini");
        keyEntry.put("apiKey", "sk-live-key");
        keyEntry.put("enabled", true);
        keyEntry.put("defaultEntry", true);
        MvcResult keyCreated = mockMvc.perform(post("/api/admin/ai/model-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(keyEntry)))
                .andExpect(status().isOk())
                .andReturn();
        long keyId =
                MAPPER.readTree(keyCreated.getResponse().getContentAsString()).path("data").path("id").asLong();

        // mode 缺省：CLI 链非空 → 落到 CLI，与 previewStream 的默认判定（previewMode）同一路径。
        mockMvc.perform(get("/api/admin/agents/" + agentId + "/ai/preview/model")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("CLI"))
                .andExpect(jsonPath("$.data.modelSpec").value("openai:muse-wonder-1.0"))
                .andExpect(jsonPath("$.data.available").value(true));

        // 显式指定 KEY（大小写不敏感），归一化后回显，链首 spec 为该站未命中时的全站默认入口。
        mockMvc.perform(get("/api/admin/agents/" + agentId + "/ai/preview/model")
                        .param("mode", "key")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("KEY"))
                .andExpect(jsonPath("$.data.modelSpec").value("openai:gpt-4o-mini"))
                .andExpect(jsonPath("$.data.available").value(true));

        // 默认入口 key 无法解密（toSelection 为 null）：该模式 available=false、modelSpec=null——
        // 旧前端只看 enabled/defaultEntry 标志位会误报「正在使用该模型」，新口径与后端组链一致。
        AdminAiModelEntryEntity broken = modelEntryMapper.selectById(keyId);
        broken.setApiKey("enc:v1:not-a-valid-ciphertext");
        modelEntryMapper.updateById(broken);
        mockMvc.perform(get("/api/admin/agents/" + agentId + "/ai/preview/model")
                        .param("mode", "KEY")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("KEY"))
                .andExpect(jsonPath("$.data.modelSpec").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.available").value(false));

        mockMvc.perform(delete("/api/admin/ai/model-entries/" + cliId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/ai/model-entries/" + keyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void modelEntryKeepsKeyOffTheApiAndPreviewUsesTheDefault() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        String secret = "super-secret-key";

        ObjectNode create = MAPPER.createObjectNode();
        create.put("displayName", "Gemini");
        create.put("modelSpec", "google:gemini-3-flash-preview");
        create.put("apiKey", secret);
        create.put("baseUrl", "https://example.test/v1");
        create.put("enabled", true);
        create.put("defaultEntry", true);
        MvcResult created = mockMvc.perform(post("/api/admin/ai/model-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.apiKeyHint").value("…-key"))
                .andExpect(jsonPath("$.data.defaultEntry").value(true))
                .andReturn();
        assertThat(created.getResponse().getContentAsString()).doesNotContain(secret).doesNotContain("enc:v1:");
        var createdData = MAPPER.readTree(created.getResponse().getContentAsString()).path("data");
        long id = createdData.path("id").asLong();
        int version = createdData.path("version").asInt();

        ObjectNode update = MAPPER.createObjectNode();
        update.put("displayName", "Gemini flash");
        update.put("modelSpec", "google:gemini-3-flash-preview");
        update.put("baseUrl", "");
        update.put("enabled", true);
        update.put("defaultEntry", true);
        update.put("version", version);
        MvcResult updated = mockMvc.perform(put("/api/admin/ai/model-entries/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Gemini flash"))
                .andExpect(jsonPath("$.data.apiKeyHint").value("…-key"))
                .andExpect(jsonPath("$.data.baseUrl").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        assertThat(updated.getResponse().getContentAsString()).doesNotContain(secret);

        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("DONE", "{\"reply\":\"hi\"}");
                    return null;
                })
                .when(agentClient)
                .chatStream(captor.capture(), any());
        ObjectNode preview = MAPPER.createObjectNode();
        preview.put("message", "hello");
        MvcResult streamed = mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(preview)))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(streamed)).andExpect(status().isOk());
        assertThat(captor.getValue().model().spec()).isEqualTo("google:gemini-3-flash-preview");
        assertThat(captor.getValue().model().apiKey()).isEqualTo(secret);
        assertThat(captor.getValue().model().baseUrl()).isNull();

        when(agentClient.runtimeStatus()).thenReturn(new AgentRuntimeStatus(
                "blocked",
                "BLOCKED",
                List.of("should-not-show"),
                "DISABLED",
                List.of(),
                new AgentRuntimeStatus.Limits(null, null, null, null, null),
                "BLOCKED",
                "Retrieval database is not configured (missing RETRIEVAL_DATABASE_URL).",
                AgentRuntimeStatus.HarnessFiles.UNKNOWN));
        mockMvc.perform(get("/api/admin/agents/" + agentId + "/ai/runtime")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.provider").value("google:gemini-3-flash-preview"))
                .andExpect(jsonPath("$.data.status.providerStatus").value("CONFIGURED"))
                .andExpect(jsonPath("$.data.status.fallbackProviders").isEmpty())
                .andExpect(jsonPath("$.data.status.retrievalStatus").value("BLOCKED"))
                .andExpect(jsonPath("$.data.status.retrievalBlockedReason")
                        .value(org.hamcrest.Matchers.containsString("RETRIEVAL_DATABASE_URL")));

        ObjectNode second = MAPPER.createObjectNode();
        second.put("displayName", "OpenAI");
        second.put("modelSpec", "openai:gpt-4o-mini");
        second.put("apiKey", "another-secret-key");
        second.put("enabled", true);
        second.put("defaultEntry", true);
        mockMvc.perform(post("/api/admin/ai/model-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultEntry").value(true));

        MvcResult listed = mockMvc.perform(get("/api/admin/ai/model-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].defaultEntry").value(true))
                .andExpect(jsonPath("$.data[1].defaultEntry").value(false))
                .andReturn();
        assertThat(listed.getResponse().getContentAsString())
                .doesNotContain(secret)
                .doesNotContain("another-secret-key")
                .doesNotContain("enc:v1:");

        ObjectNode agentEntry = MAPPER.createObjectNode();
        agentEntry.put("displayName", "Agent Gemini");
        agentEntry.put("modelSpec", "google:gemini-3-flash-preview");
        agentEntry.put("apiKey", "agent-secret-key");
        agentEntry.put("agentId", agentId);
        agentEntry.put("enabled", true);
        agentEntry.put("defaultEntry", true);
        mockMvc.perform(post("/api/admin/ai/model-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(agentEntry)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentId").value((int) agentId))
                .andExpect(jsonPath("$.data.defaultEntry").value(true));
        MvcResult scoped = mockMvc.perform(get("/api/admin/ai/model-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        boolean platformDefaultRemains = false;
        boolean agentDefault = false;
        for (var item : MAPPER.readTree(scoped.getResponse().getContentAsString()).path("data")) {
            if ("OpenAI".equals(item.path("displayName").asText())) {
                platformDefaultRemains = item.path("defaultEntry").asBoolean();
            }
            if ("Agent Gemini".equals(item.path("displayName").asText())) {
                agentDefault = item.path("defaultEntry").asBoolean();
            }
        }
        assertThat(platformDefaultRemains).isTrue();
        assertThat(agentDefault).isTrue();
        for (var item : MAPPER.readTree(scoped.getResponse().getContentAsString()).path("data")) {
            mockMvc.perform(delete("/api/admin/ai/model-entries/" + item.path("id").asLong())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void previewPinsChainEntryAndRejectsOneThatIsNotOnTheChain() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        long defaultId = createPreviewEntry(token, agentId, "默认", "openai:default-model", true);
        long altId = createPreviewEntry(token, agentId, "备用", "openai:alt-model", false);

        mockMvc.perform(get("/api/admin/agents/" + agentId + "/ai/preview/model")
                        .param("mode", "KEY")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries[0].id").value((int) defaultId))
                .andExpect(jsonPath("$.data.entries[1].id").value((int) altId))
                .andExpect(jsonPath("$.data.entries[1].displayName").value("备用"))
                .andExpect(jsonPath("$.data.entries[1].modelSpec").value("openai:alt-model"));

        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("DONE", "{\"reply\":\"hi\"}");
                    return null;
                })
                .when(agentClient)
                .chatStream(captor.capture(), any());

        ObjectNode body = MAPPER.createObjectNode();
        body.put("message", "hello");
        body.put("mode", "KEY");
        body.put("modelEntryId", altId);
        MvcResult pinned = mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(pinned)).andExpect(status().isOk());
        assertThat(captor.getValue().model().spec()).isEqualTo("openai:alt-model");
        assertThat(captor.getValue().fallbackModels()).isNull();

        mockMvc.perform(get("/api/admin/ai/model-entries").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + defaultId + ")].defaultEntry").value(org.hamcrest.Matchers.hasItem(true)));

        body.put("modelEntryId", 9_999_999L);
        MvcResult rejected = mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(rejected))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MODEL_ENTRY_UNAVAILABLE")));
    }

    @Test
    void previewDonePersistsAdminPreviewConversationWithoutMemory() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        clearConversationRows(agentId);
        createPreviewEntry(token, agentId, "preview-persist", "openai:muse-wonder-1.0", true);
        stubPreviewDone("hello back");
        Integer memoryBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_user_memory", Integer.class);

        String first = streamPreview(token, agentId, "{\"message\":\"hello\"}");
        long conversationId = conversationIdFrom(first);

        AdminAiConversationEntity conversation = conversationMapper.selectById(conversationId);
        assertThat(conversation.getSource()).isEqualTo(AdminAiConversationEntity.SOURCE_ADMIN_PREVIEW);
        assertThat(conversation.getAgentUserId()).isNull();
        assertThat(conversation.getAgentId()).isEqualTo(agentId);
        assertThat(conversation.getTitle()).isEqualTo("hello");
        List<AdminAiMessageEntity> messages = messageMapper.selectList(new LambdaQueryWrapper<AdminAiMessageEntity>()
                .eq(AdminAiMessageEntity::getConversationId, conversationId)
                .orderByAsc(AdminAiMessageEntity::getId));
        assertThat(messages).extracting(AdminAiMessageEntity::getRole).containsExactly("USER", "ASSISTANT");
        assertThat(messages).extracting(AdminAiMessageEntity::getContent).containsExactly("hello", "hello back");
        // 预览不写 user memory。
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_user_memory", Integer.class))
                .isEqualTo(memoryBefore);

        // 带 conversationId 续写同一会话：不新建会话，追加两条消息，且不再回传新的会话。
        String second = streamPreview(token, agentId, "{\"message\":\"again\",\"conversationId\":" + conversationId + "}");
        assertThat(conversationIdFrom(second)).isEqualTo(conversationId);
        assertThat(conversationMapper.selectCount(new LambdaQueryWrapper<AdminAiConversationEntity>()
                        .eq(AdminAiConversationEntity::getAgentId, agentId)))
                .isEqualTo(1L);
        assertThat(messageMapper.selectCount(new LambdaQueryWrapper<AdminAiMessageEntity>()
                        .eq(AdminAiMessageEntity::getConversationId, conversationId)))
                .isEqualTo(4L);

        mockMvc.perform(get("/api/admin/agents/" + agentId + "/ai/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].source").value("ADMIN_PREVIEW"))
                .andExpect(jsonPath("$.data.records[0].agentUserId").doesNotExist());
    }

    @Test
    void previewWithoutDoneDoesNotPersist() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        clearConversationRows(agentId);
        createPreviewEntry(token, agentId, "preview-nodone", "openai:muse-wonder-1.0", true);
        doAnswer(invocation -> {
                    awaitPreviewGate();
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("START", "{}");
                    throw new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED);
                })
                .when(agentClient)
                .chatStream(any(), any());

        String sse = streamPreview(token, agentId, "{\"message\":\"hello\"}");

        assertThat(sse).contains("EXTERNAL_DEPENDENCY_BLOCKED").doesNotContain("CONVERSATION");
        assertThat(conversationMapper.selectCount(new LambdaQueryWrapper<AdminAiConversationEntity>()
                        .eq(AdminAiConversationEntity::getAgentId, agentId)))
                .isZero();
        assertThat(messageMapper.selectCount(
                        new LambdaQueryWrapper<AdminAiMessageEntity>().eq(AdminAiMessageEntity::getAgentId, agentId)))
                .isZero();
    }

    @Test
    void previewRejectsPortalOrForeignConversationId() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        long otherAgentId = createSite(token);
        clearConversationRows(agentId);
        clearConversationRows(otherAgentId);
        long portalConversationId = insertConversation(agentId, "portal chat");
        stubPreviewDone("x");
        long foreignPreviewId = conversationIdFrom(streamPreviewOnNewEntry(token, otherAgentId));

        for (long id : new long[] {portalConversationId, foreignPreviewId, 999_999_999L}) {
            mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"hello\",\"conversationId\":" + id + "}"))
                    .andExpect(status().isNotFound());
        }
        // PORTAL 会话未被污染。
        assertThat(messageMapper.selectCount(new LambdaQueryWrapper<AdminAiMessageEntity>()
                        .eq(AdminAiMessageEntity::getConversationId, portalConversationId)))
                .isEqualTo(2L);
    }

    /**
     * 会话/消息表不在 rbac-h2-data.sql 的清理范围，agentId 在方法间会复用（IDENTITY 重置），
     * 按 agentId 对比计数的用例须先清掉前一个方法残留的行。
     */
    private void clearConversationRows(long agentId) {
        jdbcTemplate.update("DELETE FROM agent_ai_message WHERE agent_id = ?", agentId);
        jdbcTemplate.update("DELETE FROM agent_ai_conversation WHERE agent_id = ?", agentId);
    }

    /**
     * 流在 perform 返回（MockMvc 打印响应）之后才开始写：否则异步写线程与打印并发改响应头，
     * 会抛 ConcurrentModificationException（同 Issue #163 的竞态）。stub 先等闸门，streamPreview 放行。
     */
    private final AtomicReference<CountDownLatch> previewGate = new AtomicReference<>(new CountDownLatch(0));

    private void awaitPreviewGate() throws InterruptedException {
        previewGate.get().await(5, TimeUnit.SECONDS);
    }

    private void stubPreviewDone(String reply) {
        doAnswer(invocation -> {
                    awaitPreviewGate();
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("START", "{}");
                    listener.onEvent("DONE", "{\"reply\":\"" + reply + "\"}");
                    return null;
                })
                .when(agentClient)
                .chatStream(any(), any());
    }

    private String streamPreview(String token, long agentId, String body) throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        previewGate.set(gate);
        MvcResult mvcResult = mockMvc.perform(post("/api/admin/agents/" + agentId + "/ai/preview/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        gate.countDown();
        return mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String streamPreviewOnNewEntry(String token, long agentId) throws Exception {
        createPreviewEntry(token, agentId, "preview-other", "openai:muse-wonder-1.0", true);
        return streamPreview(token, agentId, "{\"message\":\"hello\"}");
    }

    /** 从 SSE 文本里取 CONVERSATION 事件的 conversationId。 */
    private static long conversationIdFrom(String sse) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                        "event:CONVERSATION\\s*\\r?\\ndata:\\{\"conversationId\":\"(\\d+)\"\\}")
                .matcher(sse);
        assertThat(matcher.find()).as("CONVERSATION event in: %s", sse).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private long createPreviewEntry(String token, long agentId, String name, String spec, boolean defaultEntry)
            throws Exception {
        ObjectNode entry = MAPPER.createObjectNode();
        entry.put("displayName", name);
        entry.put("modelSpec", spec);
        entry.put("apiKey", "sk-" + name);
        entry.put("agentId", agentId);
        entry.put("mode", "KEY");
        entry.put("enabled", true);
        entry.put("defaultEntry", defaultEntry);
        MvcResult created = mockMvc.perform(post("/api/admin/ai/model-entries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(entry)))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long createSite(String token) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("agentCode", "ai_" + Long.toHexString(System.nanoTime()));
        body.put("agentName", "AI Agent");
        body.put("agentType", "CAKE");
        MvcResult created = mockMvc.perform(post("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long insertConversation(long agentId, String title) {
        AdminAiConversationEntity conversation = new AdminAiConversationEntity();
        conversation.setAgentId(agentId);
        conversation.setAgentUserId(1L);
        conversation.setTitle(title);
        conversation.setLastMessageAt(OffsetDateTime.now(ZoneOffset.UTC));
        conversationMapper.insert(conversation);

        AdminAiMessageEntity user = new AdminAiMessageEntity();
        user.setConversationId(conversation.getId());
        user.setAgentId(agentId);
        user.setRole(AdminAiMessageEntity.ROLE_USER);
        user.setContent(title);
        messageMapper.insert(user);

        AdminAiMessageEntity assistant = new AdminAiMessageEntity();
        assistant.setConversationId(conversation.getId());
        assistant.setAgentId(agentId);
        assistant.setRole(AdminAiMessageEntity.ROLE_ASSISTANT);
        assistant.setContent("ok");
        assistant.setAgentMeta("{\"usage\":{\"requests\":2,\"toolCalls\":1,\"inputTokens\":30,\"outputTokens\":5},"
                + "\"tools\":[{\"name\":\"list_todos\",\"ok\":true}],\"skillsApplied\":[\"orchestration\"],\"x\":1}");
        messageMapper.insert(assistant);
        return conversation.getId();
    }
}
