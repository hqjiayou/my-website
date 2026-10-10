package com.mywebsite.site.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.client.AgentStreamListener;
import com.mywebsite.site.testsupport.AbstractSiteSpringBootTest;
import com.mywebsite.site.testsupport.SiteTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** §23.1：站点 SSE 只向终端用户下发回复文本与错误码，运行轨迹 / 原始错误信息不外泄。 */
@AutoConfigureMockMvc
class SiteAiChatStreamMvcTest extends AbstractSiteSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentClient agentClient;

    private RSAKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        privateKey = SiteTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
    }

    @Test
    void streamStripsRunTraceFromEndUserEvents() throws Exception {
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent("START", "{\"siteId\":\"10\",\"skillsApplied\":[\"orchestration\"]}");
                    listener.onEvent("TOOL_START", "{\"toolCallId\":\"c1\",\"toolName\":\"internal__lookup\"}");
                    listener.onEvent("TEXT_DELTA", "{\"delta\":\"hi\"}");
                    listener.onEvent(
                            "TOOL_RESULT", "{\"toolCallId\":\"c1\",\"toolName\":\"internal__lookup\",\"ok\":true}");
                    listener.onEvent("FUTURE_EVENT", "{\"secret\":\"x\"}");
                    listener.onEvent(
                            "DONE",
                            "{\"reply\":\"hi\",\"providerStatus\":\"LIVE\",\"skillsApplied\":[\"orchestration\"],"
                                    + "\"usage\":{\"requests\":1},\"tools\":[{\"name\":\"internal__lookup\",\"ok\":true}],"
                                    + "\"todos\":[]}");
                    return null;
                })
                .when(agentClient)
                .chatStream(any(), any());

        String body = streamBody();

        assertThat(body).contains("event:TEXT_DELTA", "event:DONE", "\"reply\":\"hi\"", "event:CONVERSATION");
        assertThat(body).doesNotContain("TOOL_", "internal__lookup", "orchestration", "usage", "FUTURE_EVENT");
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
