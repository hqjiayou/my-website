package com.mywebsite.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.mywebsite.infra.service.ObjectStorageService;
import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@Sql(
        scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminMediaManagementMvcTest extends AbstractAdminSpringBootTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void happyPath_uploadListDetailPreviewDelete() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token);

        byte[] jpeg = minimalJpeg();
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "application/octet-stream", jpeg);

        MvcResult uploaded = mockMvc.perform(multipart("/api/admin/sites/" + siteId + "/media")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.extension").value("jpg"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.siteId").value((int) siteId))
                .andReturn();

        long mediaId =
                MAPPER.readTree(uploaded.getResponse().getContentAsString()).path("data").path("id").asLong();
        verify(objectStorageService).put(eq("test"), anyString(), any(), eq((long) jpeg.length), eq("image/jpeg"));

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/media")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) mediaId));

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/media/" + mediaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFilename").value("photo.jpg"))
                .andExpect(jsonPath("$.data.sha256").isNotEmpty());

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/media/" + mediaId + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previewUrl").value("http://example.test/presigned"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());

        mockMvc.perform(delete("/api/admin/sites/" + siteId + "/media/" + mediaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/media/" + mediaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        verify(objectStorageService).remove(eq("test"), anyString());
    }

    @Test
    void uploadForbiddenWithoutPermission() throws Exception {
        String token = tokenFor(100L);
        long siteId = createSite(tokenFor(500L));
        MockMultipartFile file =
                new MockMultipartFile("file", "a.jpg", "image/jpeg", minimalJpeg());
        mockMvc.perform(multipart("/api/admin/sites/" + siteId + "/media")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossSiteMediaReturns404() throws Exception {
        String token = tokenFor(500L);
        long siteA = createSite(token);
        long siteB = createSite(token);

        MvcResult uploaded = mockMvc.perform(multipart("/api/admin/sites/" + siteA + "/media")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", minimalJpeg()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long mediaId =
                MAPPER.readTree(uploaded.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/admin/sites/" + siteB + "/media/" + mediaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/sites/" + siteB + "/media/" + mediaId + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/admin/sites/" + siteB + "/media/" + mediaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsSvgAndHtmlByMagic() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token);

        mockMvc.perform(multipart("/api/admin/sites/" + siteId + "/media")
                        .file(new MockMultipartFile(
                                "file",
                                "x.svg",
                                "image/svg+xml",
                                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
                                        .getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/admin/sites/" + siteId + "/media")
                        .file(new MockMultipartFile(
                                "file",
                                "x.html",
                                "text/html",
                                "<!DOCTYPE html><html></html>".getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOversizedImage() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token);

        byte[] header = minimalJpeg();
        byte[] oversized = new byte[5 * 1024 * 1024 + 64];
        System.arraycopy(header, 0, oversized, 0, header.length);
        Arrays.fill(oversized, header.length, oversized.length, (byte) 0x00);

        mockMvc.perform(multipart("/api/admin/sites/" + siteId + "/media")
                        .file(new MockMultipartFile("file", "big.jpg", "image/jpeg", oversized))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadFailsWhenStoragePutFails_marksFailedPath() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token);
        doThrow(new IllegalStateException("boom"))
                .when(objectStorageService)
                .put(anyString(), anyString(), any(), anyLong(), anyString());

        mockMvc.perform(multipart("/api/admin/sites/" + siteId + "/media")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", minimalJpeg()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isInternalServerError());

        // put 失败也须落库 FAILED，供 orphan 扫尾（非事务回滚吞掉）
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT status, deleted, object_purged_at
                FROM media_file
                WHERE site_id = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                siteId);
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("deleted")).isEqualTo(false);
        assertThat(row.get("object_purged_at")).isNull();
    }

    @Test
    void softDeleteSiteAlsoSoftDeletesMedia() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token);

        MvcResult uploaded = mockMvc.perform(multipart("/api/admin/sites/" + siteId + "/media")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", minimalJpeg()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long mediaId =
                MAPPER.readTree(uploaded.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/admin/sites/" + siteId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/media/" + mediaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private long createSite(String token) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("siteCode", "m_" + Long.toHexString(System.nanoTime()));
        body.put("siteName", "Media Site");
        body.put("siteType", "CAKE");
        MvcResult created = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    /** 最小可识别 JPEG 魔数（非完整图片亦可被本实现接受）。 */
    private static byte[] minimalJpeg() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
    }
}
