package com.mywebsite.agent.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentCitation;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.agent.dto.AgentAiMessageDto;
import com.mywebsite.agent.service.AgentAiConversationService;
import com.mywebsite.agent.service.AgentAiConversationService.ChatPreparation;
import com.mywebsite.agent.testsupport.AbstractAgentSpringBootTest;
import com.mywebsite.agent.testsupport.AgentTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AgentAiChatMvcTest extends AbstractAgentSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentAiConversationService conversationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 仅预算用例覆盖；其余用例仍走 AbstractAgentSpringBootTest 的 agent stub。 */
    @MockBean
    private AgentClient agentClient;

    private RSAKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        privateKey = AgentTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        // doThrow：避免 when().thenThrow 在重设 stub 时触发旧异常
        doThrow(new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED))
                .when(agentClient)
                .chat(any());
    }

    @Test
    void chatWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/agent/10/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatReturnsExternalDependencyBlockedWhenProviderHasNoKey() throws Exception {
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);

        mockMvc.perform(post("/api/agent/10/ai/chat")
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
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);

        mockMvc.perform(post("/api/agent/10/ai/chat")
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
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);
        String message = "a".repeat(16_001);

        mockMvc.perform(post("/api/agent/10/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    void chatRejectsBlankMessageWithoutAttachments() throws Exception {
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);

        mockMvc.perform(post("/api/agent/10/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatJson("   ", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
        verify(agentClient, never()).chat(any());
    }

    @Test
    void chatRejectsIllegalAttachmentMediaType() throws Exception {
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);

        mockMvc.perform(post("/api/agent/10/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatJson("hi", List.of(attachment("image/bmp", "YQ==")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
        verify(agentClient, never()).chat(any());
    }

    @Test
    void chatRejectsMoreThanThreeAttachments() throws Exception {
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);
        List<Map<String, String>> attachments =
                List.of(attachment("image/png", "YQ=="), attachment("image/png", "YQ=="),
                        attachment("image/png", "YQ=="), attachment("image/png", "YQ=="));

        mockMvc.perform(post("/api/agent/10/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatJson("hi", attachments)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
        verify(agentClient, never()).chat(any());
    }

    @Test
    void chatRejectsAttachmentLargerThanTwoMebibytes() throws Exception {
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);
        String data = Base64.getEncoder().encodeToString(new byte[2 * 1024 * 1024 + 1]);

        mockMvc.perform(post("/api/agent/10/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatJson("hi", List.of(attachment("image/jpeg", data)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
        verify(agentClient, never()).chat(any());
    }

    @Test
    void chatAllowsBlankMessageWhenAttachmentIsPresent() throws Exception {
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);
        String data =
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

        mockMvc.perform(post("/api/agent/10/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatJson("", List.of(attachment("image/png", data)))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode()));

        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(agentClient).chat(captor.capture());
        assertEquals("", captor.getValue().message());
        assertEquals(1, captor.getValue().attachments().size());
        assertEquals("image/png", captor.getValue().attachments().get(0).mediaType());
        assertEquals(data, captor.getValue().attachments().get(0).data());
    }

    @Test
    void chatForwardsExtractedDocumentTextToAgent() throws Exception {
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);
        String data = Base64.getEncoder()
                .encodeToString("营业时间 9:00-18:00".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(post("/api/agent/10/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatJsonWithDocuments("总结一下", List.of(document("说明.txt", "text/plain", data)))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode()));

        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(agentClient).chat(captor.capture());
        assertEquals(1, captor.getValue().documents().size());
        assertEquals("说明.txt", captor.getValue().documents().get(0).name());
        assertEquals("营业时间 9:00-18:00", captor.getValue().documents().get(0).text());
        assertEquals(false, captor.getValue().documents().get(0).truncated());
    }

    @Test
    void chatPersistsDocumentDescriptorWithoutContent() throws Exception {
        doReturn(new AgentChatResponse("summary", null, "LIVE", List.of(), AgentRunMeta.EMPTY, List.of()))
                .when(agentClient)
                .chat(any());
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);
        String data = Base64.getEncoder()
                .encodeToString("合同正文".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String body = mockMvc.perform(post("/api/agent/10/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatJsonWithDocuments("", List.of(document("合同.txt", "text/plain", data)))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long conversationId = Long.parseLong(
                new ObjectMapper().readTree(body).path("data").path("conversationId").asText());

        var listed = conversationService.listMessages(10L, 200L, conversationId);
        assertEquals(2, listed.size());
        assertEquals(1, listed.get(0).attachments().size());
        var descriptor = listed.get(0).attachments().get(0);
        assertEquals("DOCUMENT", descriptor.kind());
        assertEquals("合同.txt", descriptor.fileName());
        assertEquals(null, descriptor.data());
    }

    @Test
    void chatRejectsUnsupportedDocumentExtension() throws Exception {
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);

        mockMvc.perform(post("/api/agent/10/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatJsonWithDocuments(
                                "hi", List.of(document("evil.exe", "application/octet-stream", "YQ==")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
        verify(agentClient, never()).chat(any());
    }

    @Test
    void chatRejectsPdfWithoutTextLayer() throws Exception {
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);
        byte[] blankPdf;
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            blankPdf = out.toByteArray();
        }
        String data = Base64.getEncoder().encodeToString(blankPdf);

        mockMvc.perform(post("/api/agent/10/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatJsonWithDocuments(
                                "总结", List.of(document("scan.pdf", "application/pdf", data)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
        verify(agentClient, never()).chat(any());
    }

    @Test
    void chatRejectsMismatchedAgentId() throws Exception {
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);

        mockMvc.perform(post("/api/agent/999/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void chatPersistsCitationsAndMessageListReadsThemBack() throws Exception {
        doReturn(new AgentChatResponse(
                        "grounded",
                        null,
                        "LIVE",
                        List.of(),
                        AgentRunMeta.EMPTY,
                        List.of(new AgentCitation("kb:9", 3, 0.75d))))
                .when(agentClient)
                .chat(any());
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);

        String body = mockMvc.perform(post("/api/agent/10/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long conversationId = Long.parseLong(
                new ObjectMapper().readTree(body).path("data").path("conversationId").asText());

        String assistantMeta = jdbcTemplate.queryForObject(
                "SELECT agent_meta FROM site_ai_message WHERE conversation_id = ? AND role = 'ASSISTANT'",
                String.class,
                conversationId);
        String userMeta = jdbcTemplate.queryForObject(
                "SELECT agent_meta FROM site_ai_message WHERE conversation_id = ? AND role = 'USER'",
                String.class,
                conversationId);
        assertNull(userMeta);
        JsonNode stored = new ObjectMapper().readTree(assistantMeta);
        assertTrue(stored.has("citations"));
        assertEquals("kb:9", stored.path("citations").path(0).path("sourceId").asText());
        assertEquals(3, stored.path("citations").path(0).path("chunkIndex").asInt());
        assertEquals(0.75d, stored.path("citations").path(0).path("score").asDouble());

        List<AgentAiMessageDto> listed = conversationService.listMessages(10L, 200L, conversationId);
        assertEquals(2, listed.size());
        assertNull(listed.get(0).citations());
        assertEquals(List.of(new AgentAiMessageDto.Citation("kb:9", 3, 0.75d)), listed.get(1).citations());
    }

    @Test
    void renameConversationRouteTrimsTitle() throws Exception {
        ChatPreparation prepared = conversationService.prepare(10L, 200L, null);
        conversationService.recordExchange(prepared.conversation(), "hello", "ok", List.of(), null);
        long id = prepared.conversation().getId();
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);

        mockMvc.perform(patch("/api/agent/10/ai/conversations/" + id)
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
        String token = AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);

        mockMvc.perform(delete("/api/agent/10/ai/conversations/" + id)
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

    private static Map<String, String> attachment(String mediaType, String data) {
        Map<String, String> attachment = new LinkedHashMap<>();
        attachment.put("mediaType", mediaType);
        attachment.put("data", data);
        return attachment;
    }

    private static String chatJson(String message, List<Map<String, String>> attachments) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        if (attachments != null) {
            body.put("attachments", attachments);
        }
        return new ObjectMapper().writeValueAsString(body);
    }

    private static Map<String, String> document(String fileName, String mediaType, String data) {
        Map<String, String> document = new LinkedHashMap<>();
        document.put("fileName", fileName);
        document.put("mediaType", mediaType);
        document.put("data", data);
        return document;
    }

    private static String chatJsonWithDocuments(String message, List<Map<String, String>> documents)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("documents", documents);
        return new ObjectMapper().writeValueAsString(body);
    }
}
