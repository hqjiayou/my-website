package com.mywebsite.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 媒体库平台化（#212）：scope 可见规则、跨站引用、删除拦截与删站级联。
 */
@AutoConfigureMockMvc
@Sql(
        scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminMediaPlatformMvcTest extends AbstractAdminSpringBootTest {

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
    void agentScopeFileInvisibleToOtherAgentInBothViews() throws Exception {
        String token = tokenFor(500L);
        long agentA = createSite(token);
        long agentB = createSite(token);
        long mediaId = uploadMedia(token, agentA);

        // 上传默认 scope=AGENT、无引用
        mockMvc.perform(get("/api/admin/agents/" + agentA + "/media/" + mediaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("AGENT"))
                .andExpect(jsonPath("$.data.referenceCount").value(0));

        mockMvc.perform(get("/api/admin/agents/" + agentB + "/media")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/admin/agents/" + agentB + "/media?view=platform")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/admin/agents/" + agentB + "/media/" + mediaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/agents/" + agentB + "/media/" + mediaId + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void platformScopeReferenceFlowStoresObjectOnce() throws Exception {
        String token = tokenFor(500L);
        long agentA = createSite(token);
        long agentB = createSite(token);
        long mediaId = uploadMedia(token, agentA);

        setScope(token, agentA, mediaId, "PLATFORM");

        // B 平台库可见、尚未引用
        mockMvc.perform(get("/api/admin/agents/" + agentB + "/media?view=platform")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) mediaId))
                .andExpect(jsonPath("$.data.records[0].agentId").value((int) agentA))
                .andExpect(jsonPath("$.data.records[0].scope").value("PLATFORM"))
                .andExpect(jsonPath("$.data.records[0].referenced").value(false));

        // B 本站库引用前仍为空
        mockMvc.perform(get("/api/admin/agents/" + agentB + "/media")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // 来源站本人 reference 为幂等无操作
        mockMvc.perform(post("/api/admin/agents/" + agentA + "/media/" + mediaId + "/reference")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // B 引用（重复引用幂等）
        mockMvc.perform(post("/api/admin/agents/" + agentB + "/media/" + mediaId + "/reference")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/agents/" + agentB + "/media/" + mediaId + "/reference")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // B 本站库出现引用项
        mockMvc.perform(get("/api/admin/agents/" + agentB + "/media")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) mediaId))
                .andExpect(jsonPath("$.data.records[0].referenced").value(true));

        // B 平台库此时 referenced=true
        mockMvc.perform(get("/api/admin/agents/" + agentB + "/media?view=platform")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].referenced").value(true));

        // B 详情/预览可用，引用计数为 1
        mockMvc.perform(get("/api/admin/agents/" + agentB + "/media/" + mediaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referenceCount").value(1));
        mockMvc.perform(get("/api/admin/agents/" + agentB + "/media/" + mediaId + "/preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previewUrl").value("http://example.test/presigned"));

        // 引用不复制对象：全程 put 只发生 1 次（A 的上传）
        verify(objectStorageService, times(1)).put(anyString(), anyString(), any(), anyLong(), anyString());
    }

    @Test
    void deleteBlockedWhileReferencedThenSucceedsAfterUnreference() throws Exception {
        String token = tokenFor(500L);
        long agentA = createSite(token);
        long agentB = createSite(token);
        long mediaId = uploadMedia(token, agentA);
        setScope(token, agentA, mediaId, "PLATFORM");
        mockMvc.perform(post("/api/admin/agents/" + agentB + "/media/" + mediaId + "/reference")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/agents/" + agentA + "/media/" + mediaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("メディアは他のサイトから参照されているため削除できません"));

        mockMvc.perform(delete("/api/admin/agents/" + agentB + "/media/" + mediaId + "/reference")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/agents/" + agentA + "/media/" + mediaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void agentDeletionCascadesReferencesBothDirections() throws Exception {
        String token = tokenFor(500L);
        long agentA = createSite(token);
        long agentB = createSite(token);
        long mediaId = uploadMedia(token, agentA);
        setScope(token, agentA, mediaId, "PLATFORM");
        mockMvc.perform(post("/api/admin/agents/" + agentB + "/media/" + mediaId + "/reference")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(referenceCount()).isEqualTo(1);

        // 删来源站 A：指向其文件的引用行清干净，B 库不再出现该文件
        mockMvc.perform(delete("/api/admin/agents/" + agentA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(referenceCount()).isZero();
        mockMvc.perform(get("/api/admin/agents/" + agentB + "/media")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // 反方向：B 引用 C 的文件后删 B（引用站），C 的文件不再被死引用阻塞删除
        long agentC = createSite(token);
        long mediaOfC = uploadMedia(token, agentC);
        setScope(token, agentC, mediaOfC, "PLATFORM");
        mockMvc.perform(post("/api/admin/agents/" + agentB + "/media/" + mediaOfC + "/reference")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(referenceCount()).isEqualTo(1);

        mockMvc.perform(delete("/api/admin/agents/" + agentB).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(referenceCount()).isZero();

        mockMvc.perform(delete("/api/admin/agents/" + agentC + "/media/" + mediaOfC)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void scopeAndReferenceEdgeCases() throws Exception {
        String token = tokenFor(500L);
        long agentA = createSite(token);
        long agentB = createSite(token);
        long mediaId = uploadMedia(token, agentA);

        // scope 非法值 → 400
        mockMvc.perform(put("/api/admin/agents/" + agentA + "/media/" + mediaId + "/scope")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"PUBLIC\"}"))
                .andExpect(status().isBadRequest());

        // 非来源站改 scope → 404
        mockMvc.perform(put("/api/admin/agents/" + agentB + "/media/" + mediaId + "/scope")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"PLATFORM\"}"))
                .andExpect(status().isNotFound());

        // 引用 AGENT 私有文件（非来源站）→ 404
        mockMvc.perform(post("/api/admin/agents/" + agentB + "/media/" + mediaId + "/reference")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        // 来源站本人取消引用 → 400
        mockMvc.perform(delete("/api/admin/agents/" + agentA + "/media/" + mediaId + "/reference")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        // 取消不存在的引用 → 404
        mockMvc.perform(delete("/api/admin/agents/" + agentB + "/media/" + mediaId + "/reference")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        // view 非法值 → 400
        mockMvc.perform(get("/api/admin/agents/" + agentA + "/media?view=bogus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private int referenceCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM media_reference", Integer.class);
        return count != null ? count : 0;
    }

    private void setScope(String token, long agentId, long mediaId, String scope) throws Exception {
        mockMvc.perform(put("/api/admin/agents/" + agentId + "/media/" + mediaId + "/scope")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"" + scope + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value(scope));
    }

    private long uploadMedia(String token, long agentId) throws Exception {
        MvcResult uploaded = mockMvc.perform(multipart("/api/admin/agents/" + agentId + "/media")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", minimalJpeg()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(uploaded.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long createSite(String token) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("agentCode", "m_" + Long.toHexString(System.nanoTime()));
        body.put("agentName", "Media Agent");
        body.put("agentType", "CAKE");
        MvcResult created = mockMvc.perform(post("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private static byte[] minimalJpeg() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
    }
}
