package com.mywebsite.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.mywebsite.infra.service.ObjectStorageService;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@Sql(
        scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminContentManagementMvcTest extends AbstractAdminSpringBootTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ObjectStorageService objectStorageService;

    @BeforeEach
    void stubStorage() {
        doNothing().when(objectStorageService).put(anyString(), anyString(), any(), anyLong(), anyString());
        doNothing().when(objectStorageService).remove(anyString(), anyString());
        when(objectStorageService.presignedGet(anyString(), anyString(), any(Duration.class)))
                .thenReturn("http://example.test/presigned");
    }

    private String tokenFor(long userId) throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        return AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", userId);
    }

    @Test
    void happyPath_createListFilterGetUpdateStatusDelete() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);

        ObjectNode create = MAPPER.createObjectNode();
        create.put("type", "hero");
        create.put("title", "Welcome");
        create.put("body", "Hello body");
        create.put("status", "DRAFT");
        create.put("sortOrder", 10);

        MvcResult created = mockMvc.perform(post("/api/admin/sites/" + agentId + "/contents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("hero"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.publishedAt").doesNotExist())
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn();

        long contentId =
                MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        ObjectNode news = MAPPER.createObjectNode();
        news.put("type", "news");
        news.put("title", "News");
        news.put("body", "n");
        news.put("status", "PUBLISHED");
        mockMvc.perform(post("/api/admin/sites/" + agentId + "/contents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(news)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());

        mockMvc.perform(get("/api/admin/sites/" + agentId + "/contents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/admin/sites/" + agentId + "/contents")
                        .param("type", "hero")
                        .param("status", "DRAFT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) contentId));

        mockMvc.perform(get("/api/admin/sites/" + agentId + "/contents/" + contentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Welcome"));

        ObjectNode update = MAPPER.createObjectNode();
        update.put("type", "hero");
        update.put("title", "Welcome Updated");
        update.put("body", "Hello body 2");
        update.put("sortOrder", 5);
        update.put("version", 0);
        mockMvc.perform(put("/api/admin/sites/" + agentId + "/contents/" + contentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Welcome Updated"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").value(1));

        ObjectNode statusBody = MAPPER.createObjectNode();
        statusBody.put("status", "PUBLISHED");
        statusBody.put("version", 1);
        mockMvc.perform(patch("/api/admin/sites/" + agentId + "/contents/" + contentId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(statusBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(delete("/api/admin/sites/" + agentId + "/contents/" + contentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/sites/" + agentId + "/contents/" + contentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createForbiddenWithoutPermission() throws Exception {
        String token = tokenFor(100L);
        long agentId = createSite(tokenFor(500L));
        ObjectNode body = MAPPER.createObjectNode();
        body.put("type", "faq");
        body.put("title", "Q");
        body.put("status", "DRAFT");
        mockMvc.perform(post("/api/admin/sites/" + agentId + "/contents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void optimisticConflictOnStaleVersion() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        long contentId = createDraft(token, agentId, "hero", "A");

        ObjectNode update = MAPPER.createObjectNode();
        update.put("type", "hero");
        update.put("title", "B");
        update.put("version", 0);
        mockMvc.perform(put("/api/admin/sites/" + agentId + "/contents/" + contentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        update.put("title", "C");
        update.put("version", 0);
        mockMvc.perform(put("/api/admin/sites/" + agentId + "/contents/" + contentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(update)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidTypeAndCrossAgentCoverMedia() throws Exception {
        String token = tokenFor(500L);
        long agentA = createSite(token);
        long agentB = createSite(token);
        long mediaOnB = uploadMedia(token, agentB);

        ObjectNode badType = MAPPER.createObjectNode();
        badType.put("type", "banner");
        badType.put("title", "X");
        badType.put("status", "DRAFT");
        mockMvc.perform(post("/api/admin/sites/" + agentA + "/contents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(badType)))
                .andExpect(status().isBadRequest());

        ObjectNode crossCover = MAPPER.createObjectNode();
        crossCover.put("type", "about");
        crossCover.put("title", "About");
        crossCover.put("status", "DRAFT");
        crossCover.put("coverMediaId", mediaOnB);
        mockMvc.perform(post("/api/admin/sites/" + agentA + "/contents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(crossCover)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void coverMediaReadyBindAndDeleteMediaRejectedWhenReferenced() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        long mediaId = uploadMedia(token, agentId);

        ObjectNode create = MAPPER.createObjectNode();
        create.put("type", "cta");
        create.put("title", "CTA");
        create.put("status", "DRAFT");
        create.put("coverMediaId", mediaId);
        mockMvc.perform(post("/api/admin/sites/" + agentId + "/contents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverMediaId").value((int) mediaId));

        mockMvc.perform(delete("/api/admin/sites/" + agentId + "/media/" + mediaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void clearCoverAndBodyAllowsMediaDelete() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        long mediaId = uploadMedia(token, agentId);

        ObjectNode create = MAPPER.createObjectNode();
        create.put("type", "cta");
        create.put("title", "CTA");
        create.put("body", "with cover");
        create.put("status", "DRAFT");
        create.put("coverMediaId", mediaId);
        MvcResult created = mockMvc.perform(post("/api/admin/sites/" + agentId + "/contents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverMediaId").value((int) mediaId))
                .andExpect(jsonPath("$.data.body").value("with cover"))
                .andReturn();
        long contentId =
                MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        ObjectNode clear = MAPPER.createObjectNode();
        clear.put("type", "cta");
        clear.put("title", "CTA");
        clear.putNull("body");
        clear.putNull("coverMediaId");
        clear.put("version", 0);
        mockMvc.perform(put("/api/admin/sites/" + agentId + "/contents/" + contentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(clear)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverMediaId").doesNotExist())
                .andExpect(jsonPath("$.data.body").doesNotExist())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(delete("/api/admin/sites/" + agentId + "/media/" + mediaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void crossAgentContentReturns404() throws Exception {
        String token = tokenFor(500L);
        long agentA = createSite(token);
        long agentB = createSite(token);
        long contentId = createDraft(token, agentA, "service", "Svc");

        mockMvc.perform(get("/api/admin/sites/" + agentB + "/contents/" + contentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/admin/sites/" + agentB + "/contents/" + contentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void softDeleteAgentAlsoSoftDeletesContent() throws Exception {
        String token = tokenFor(500L);
        long agentId = createSite(token);
        long contentId = createDraft(token, agentId, "case", "Case");

        mockMvc.perform(delete("/api/admin/sites/" + agentId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/sites/" + agentId + "/contents/" + contentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private long createSite(String token) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("agentCode", "c_" + Long.toHexString(System.nanoTime()));
        body.put("agentName", "Content Agent");
        body.put("agentType", "CAKE");
        MvcResult created = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long createDraft(String token, long agentId, String type, String title) throws Exception {
        ObjectNode create = MAPPER.createObjectNode();
        create.put("type", type);
        create.put("title", title);
        create.put("status", "DRAFT");
        MvcResult created = mockMvc.perform(post("/api/admin/sites/" + agentId + "/contents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long uploadMedia(String token, long agentId) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", minimalJpeg());
        MvcResult uploaded = mockMvc.perform(multipart("/api/admin/sites/" + agentId + "/media")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(uploaded.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private static byte[] minimalJpeg() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
    }
}
