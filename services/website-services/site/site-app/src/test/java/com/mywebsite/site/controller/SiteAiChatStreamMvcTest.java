package com.mywebsite.site.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.client.AgentStreamListener;
import com.mywebsite.site.dto.SiteAiMessageDto;
import com.mywebsite.site.service.SiteAiConversationService;
import com.mywebsite.site.testsupport.AbstractSiteSpringBootTest;
import com.mywebsite.site.testsupport.SiteTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
class SiteAiChatStreamMvcTest extends AbstractSiteSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SiteAiConversationService conversationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AgentClient agentClient;

    private RSAKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        privateKey = SiteTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
    }

    @Test
    void streamForwardsProcessEventsWithWhitelistedFieldsOnly() throws Exception {
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("START", "{\"siteId\":\"10\",\"skillsApplied\":[\"orchestration\"]}");
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
                "SELECT agent_meta FROM site_ai_message WHERE conversation_id = ? AND role = 'ASSISTANT'",
                String.class,
                conversationId);
        assertThat(assistantMeta).contains("\"citations\"", "kb:4", "\"chunkIndex\":1");
        assertThat(new ObjectMapper().readTree(assistantMeta).path("citations").path(0).path("score").asDouble())
                .isEqualTo(0.25d);

        List<SiteAiMessageDto> listed = conversationService.listMessages(10L, 200L, conversationId);
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).citations()).isNull();
        assertThat(listed.get(1).citations()).containsExactly(new SiteAiMessageDto.Citation("kb:4", 1, 0.25d));
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

        List<SiteAiMessageDto> listed = conversationService.listMessages(10L, 200L, conversationId);
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).process()).isNull();
        SiteAiMessageDto.Process process = listed.get(1).process();
        assertThat(process).isNotNull();
        assertThat(process.tools()).containsExactly(new SiteAiMessageDto.Process.Tool("search_site_knowledge", true));
        assertThat(process.searches()).containsExactly(new SiteAiMessageDto.Process.Search("hours", 2));
    }

    private String streamBody() throws Exception {
        String token = SiteTestJwtSigner.signSiteAccess(privateKey, "test-issuer", "SITE", 200L, 10L);
        MvcResult mvcResult = mockMvc.perform(post("/api/site/10/ai/chat/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
