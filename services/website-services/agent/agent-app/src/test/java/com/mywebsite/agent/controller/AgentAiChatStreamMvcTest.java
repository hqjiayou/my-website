package com.mywebsite.agent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.client.AgentStreamListener;
import com.mywebsite.agent.dto.AgentAiMessageDto;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.service.AgentAiConversationService;
import com.mywebsite.agent.testsupport.AbstractAgentSpringBootTest;
import com.mywebsite.agent.testsupport.AgentTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 站点 SSE 向终端用户下发回复文本、错误码与过程事件（思考/工具/检索摘要白名单）；工具返回正文、运行轨迹明细与原始错误信息不外泄。 */
@AutoConfigureMockMvc
class AgentAiChatStreamMvcTest extends AbstractAgentSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentAiConversationService conversationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AgentClient agentClient;

    private RSAKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        privateKey = AgentTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        // 模式必填 + 所选模式要有可用入口后（Issue #145），给 agent 10 种一条 KEY 默认入口，
        // 否则请求会在组链守卫处 BLOCKED、到不了被 mock 的 agentClient。
        jdbcTemplate.update("DELETE FROM agent_model_entry");
        jdbcTemplate.update(
                "INSERT INTO agent_model_entry (display_name, model_spec, api_key, agent_id, enabled, is_default, mode)"
                        + " VALUES ('Test Key', 'google:test-model', 'test-api-key', 10, TRUE, TRUE, 'KEY')");
        // 链预设口径（Issue #210 UoW-7）：链由 agent 引用的预设决定，把刚种的入口装进
        // 预设 9001 并让 agent 10 引用，否则组链为空、请求到不了被 mock 的 agentClient。
        Long seededEntryId = jdbcTemplate.queryForObject("SELECT id FROM agent_model_entry", Long.class);
        jdbcTemplate.update("DELETE FROM agent_ai_chain_preset");
        jdbcTemplate.update(
                "INSERT INTO agent_ai_chain_preset (id, name, purpose, entry_ids) VALUES (9001, 'test-chain', 'CHAT', ?)",
                "[" + seededEntryId + "]");
        jdbcTemplate.update("UPDATE agent SET chain_preset_id = 9001 WHERE id = 10");
    }

    @Test
    void streamForwardsProcessEventsWithWhitelistedFieldsOnly() throws Exception {
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("START", "{\"agentId\":\"10\",\"skillsApplied\":[\"orchestration\"]}");
                    listener.onEvent("THINKING", "{\"delta\":\"let me check\"}");
                    listener.onEvent("TOOL_START", "{\"toolCallId\":\"c1\",\"toolName\":\"search_site_knowledge\"}");
                    listener.onEvent("TEXT_DELTA", "{\"delta\":\"hi\"}");
                    listener.onEvent(
                            "TOOL_RESULT",
                            "{\"toolCallId\":\"c1\",\"toolName\":\"search_site_knowledge\",\"ok\":true,"
                                    + "\"summary\":{\"query\":\"hours\",\"hitCount\":1,"
                                    + "\"sources\":[{\"sourceId\":\"kb:1\",\"chunkIndex\":2,\"score\":0.9}],"
                                    + "\"content\":\"SECRET-CONTENT\"}}");
                    listener.onEvent("FUTURE_EVENT", "{\"secret\":\"x\"}");
                    listener.onEvent(
                            "DONE",
                            "{\"reply\":\"hi\",\"providerStatus\":\"LIVE\",\"skillsApplied\":[\"orchestration\"],"
                                    + "\"usage\":{\"requests\":1},\"tools\":[{\"name\":\"search_site_knowledge\",\"ok\":true}],"
                                    + "\"todos\":[]}");
                    return null;
                })
                .when(agentClient)
                .chatStream(any(), any());

        String body = streamBody();

        assertThat(body)
                .contains(
                        "event:THINKING",
                        "\"delta\":\"let me check\"",
                        "event:TOOL_START",
                        "event:TOOL_RESULT",
                        "\"toolName\":\"search_site_knowledge\"",
                        "\"hitCount\":1",
                        "\"sourceId\":\"kb:1\"",
                        "event:TEXT_DELTA",
                        "event:DONE",
                        "\"reply\":\"hi\"",
                        "event:CONVERSATION");
        // 工具返回正文、DONE 的用量/轨迹、未知事件与 START 的 skills 仍不下发。
        assertThat(body).doesNotContain("SECRET-CONTENT", "orchestration", "usage", "FUTURE_EVENT", "\"tools\"");
    }

    @Test
    void streamErrorKeepsOnlyWhitelistedCode() throws Exception {
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent(
                            "ERROR",
                            "{\"errorCode\":\"AGENT_BUDGET_EXCEEDED\",\"message\":\"request_limit of 10 at http://internal\"}");
                    return null;
                })
                .when(agentClient)
                .chatStream(any(), any());

        String body = streamBody();

        assertThat(body).contains("event:ERROR", "\"errorCode\":\"AGENT_BUDGET_EXCEEDED\"");
        assertThat(body).doesNotContain("http://internal", "message", "event:CONVERSATION");
    }

    @Test
    void streamErrorWithUnknownCodeFallsBackToSystemError() throws Exception {
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("ERROR", "{\"errorCode\":\"PROVIDER_STACKTRACE\",\"message\":\"boom\"}");
                    return null;
                })
                .when(agentClient)
                .chatStream(any(), any());

        String body = streamBody();

        assertThat(body).contains("\"errorCode\":\"SYSTEM_ERROR\"");
        assertThat(body).doesNotContain("PROVIDER_STACKTRACE", "boom");
    }

    @Test
    void streamPersistsCitationsOnAssistantMessage() throws Exception {
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("TEXT_DELTA", "{\"delta\":\"hi\"}");
                    listener.onEvent(
                            "DONE",
                            "{\"reply\":\"hi\",\"providerStatus\":\"LIVE\",\"todos\":[],"
                                    + "\"citations\":[{\"sourceId\":\"kb:4\",\"chunkIndex\":1,\"score\":0.25}]}");
                    return null;
                })
                .when(agentClient)
                .chatStream(any(), any());

        String body = streamBody();
        Matcher matcher = Pattern.compile("\"conversationId\":\"(\\d+)\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        long conversationId = Long.parseLong(matcher.group(1));

        String assistantMeta = jdbcTemplate.queryForObject(
                "SELECT agent_meta FROM agent_ai_message WHERE conversation_id = ? AND role = 'ASSISTANT'",
                String.class,
                conversationId);
        assertThat(assistantMeta).contains("\"citations\"", "kb:4", "\"chunkIndex\":1");
        assertThat(new ObjectMapper().readTree(assistantMeta).path("citations").path(0).path("score").asDouble())
                .isEqualTo(0.25d);

        List<AgentAiMessageDto> listed = conversationService.listMessages(10L, 200L, conversationId);
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).citations()).isNull();
        assertThat(listed.get(1).citations()).containsExactly(new AgentAiMessageDto.Citation("kb:4", 1, 0.25d));
    }

    @Test
    void streamKeepsCitationTitleAndLocationOnClientEventAndStoredMeta() throws Exception {
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent(
                            "DONE",
                            "{\"reply\":\"查到了\",\"providerStatus\":\"LIVE\",\"citations\":[{"
                                    + "\"sourceId\":\"kb:4\",\"chunkIndex\":1,\"score\":0.25,\"cite\":2,"
                                    + "\"title\":\"10月考勤表\","
                                    + "\"location\":{\"page\":3,\"sheet\":\"Hours\",\"rowStart\":2,\"rowEnd\":5}}]}");
                    return null;
                })
                .when(agentClient)
                .chatStream(any(), any());

        String body = streamBody();
        assertThat(body).contains("10月考勤表", "\"page\":3", "Hours", "\"cite\":2");
        assertThat(body).doesNotContain("retrievalDegraded");

        Matcher matcher = Pattern.compile("\"conversationId\":\"(\\d+)\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        long conversationId = Long.parseLong(matcher.group(1));
        String assistantMeta = jdbcTemplate.queryForObject(
                "SELECT agent_meta FROM agent_ai_message WHERE conversation_id = ? AND role = 'ASSISTANT'",
                String.class,
                conversationId);
        assertThat(assistantMeta).contains("10月考勤表", "\"page\":3", "\"cite\":2");

        AgentAiMessageDto.Citation citation =
                conversationService.listMessages(10L, 200L, conversationId).get(1).citations().getFirst();
        assertThat(citation.title()).isEqualTo("10月考勤表");
        assertThat(citation.cite()).isEqualTo(2);
        assertThat(citation.location().page()).isEqualTo(3);
        assertThat(citation.location().sheet()).isEqualTo("Hours");
        assertThat(citation.location().rowStart()).isEqualTo(2);
        assertThat(citation.location().rowEnd()).isEqualTo(5);
    }

    @Test
    void streamPersistsProcessSummaryOnAssistantMessage() throws Exception {
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("TEXT_DELTA", "{\"delta\":\"hi\"}");
                    listener.onEvent(
                            "DONE",
                            "{\"reply\":\"hi\",\"providerStatus\":\"LIVE\",\"todos\":[],"
                                    + "\"tools\":[{\"name\":\"search_site_knowledge\",\"ok\":true}],"
                                    + "\"searches\":[{\"query\":\"hours\",\"hitCount\":2,"
                                    + "\"sources\":[{\"sourceId\":\"kb:4\",\"chunkIndex\":1,\"score\":0.25}]}]}");
                    return null;
                })
                .when(agentClient)
                .chatStream(any(), any());

        String body = streamBody();
        Matcher matcher = Pattern.compile("\"conversationId\":\"(\\d+)\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        long conversationId = Long.parseLong(matcher.group(1));

        List<AgentAiMessageDto> listed = conversationService.listMessages(10L, 200L, conversationId);
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).process()).isNull();
        AgentAiMessageDto.Process process = listed.get(1).process();
        assertThat(process).isNotNull();
        assertThat(process.tools()).containsExactly(new AgentAiMessageDto.Process.Tool("search_site_knowledge", true));
        assertThat(process.searches()).containsExactly(new AgentAiMessageDto.Process.Search("hours", 2));
    }

    @Test
    void streamForwardsTimezoneAndKeepsOldPayloadNullable() throws Exception {
        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("DONE", "{\"reply\":\"hi\",\"providerStatus\":\"LIVE\"}");
                    return null;
                })
                .when(agentClient)
                .chatStream(captor.capture(), any());

        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);
        dispatch(token, "{\"message\":\"hello\",\"mode\":\"KEY\"}");
        dispatch(token, "{\"message\":\"hello\",\"mode\":\"KEY\",\"timezone\":\"Pacific/Pago_Pago\"}");

        assertThat(captor.getAllValues()).hasSize(2);
        assertThat(captor.getAllValues().get(0).timezone()).isNull();
        assertThat(captor.getAllValues().get(1).timezone()).isEqualTo("Pacific/Pago_Pago");
        verify(agentClient, org.mockito.Mockito.times(2)).chatStream(any(), any());
    }

    private void dispatch(String token, String json) throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/agent/10/ai/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());
    }

    private String streamBody() throws Exception {
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);
        MvcResult mvcResult = mockMvc.perform(post("/api/agent/10/ai/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\",\"mode\":\"KEY\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
