package com.mywebsite.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionResultDto;
import com.mywebsite.admin.service.AdminAiExperienceExtractionService;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 经验沉淀人审端点（Issue #184 UoW-3）：状态机服务走真实实现＋H2 夹具表，
 * 只把提炼编排（UoW-2，依赖对话数据与执行层）以 @MockBean 固定汇总。
 * 权限口径：user 500（admin 角色）有 ai-model read/update；user 100（editor 角色）
 * 两者皆无——读、写端点都应 403。每个方法前清空条目表保 L 编号从 6 起确定。
 */
@AutoConfigureMockMvc
@Sql(
        scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminAiExperienceLessonMvcTest extends AbstractAdminSpringBootTest {

    private static final String BASE = "/api/admin/ai/experience-lessons";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AdminAiExperienceExtractionService extractionService;

    @BeforeEach
    void cleanLessons() {
        jdbcTemplate.execute("DELETE FROM agent_experience_lesson");
    }

    private String tokenFor(long userId) throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        return AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", userId);
    }

    private long createCandidate(String token, String title, String lesson) throws Exception {
        MvcResult result =
                mockMvc.perform(post(BASE)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"" + title + "\",\"symptom\":\"症状\",\"lesson\":\""
                                        + lesson + "\",\"evidence\":\"证据\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("id").asLong();
    }

    @Test
    void createThenListAndDetail() throws Exception {
        String token = tokenFor(500L);
        long id = createCandidate(token, "集合问题先枚举", "先 list 再逐份抽取");

        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].title").value("集合问题先枚举"))
                .andExpect(jsonPath("$.data[0].lessonSummary").value("先 list 再逐份抽取"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].lessonNo").doesNotExist());

        mockMvc.perform(get(BASE + "?status=APPROVED").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get(BASE + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symptom").value("症状"))
                .andExpect(jsonPath("$.data.lesson").value("先 list 再逐份抽取"))
                .andExpect(jsonPath("$.data.evidence").value("证据"))
                .andExpect(jsonPath("$.data.sourceAgentId").doesNotExist())
                .andExpect(jsonPath("$.data.sourceConversationId").doesNotExist())
                .andExpect(jsonPath("$.data.sourceMessageId").doesNotExist());
    }

    @Test
    void approveWithRevisionAssignsSequentialLessonNumbers() throws Exception {
        String token = tokenFor(500L);
        long first = createCandidate(token, "第一条", "原文教训");
        long second = createCandidate(token, "第二条", "另一条教训");

        mockMvc.perform(post(BASE + "/" + first + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lesson\":\"改写后的教训\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.lessonNo").value(6))
                .andExpect(jsonPath("$.data.lesson").value("改写后的教训"))
                .andExpect(jsonPath("$.data.title").value("第一条"));

        mockMvc.perform(post(BASE + "/" + second + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonNo").value(7))
                .andExpect(jsonPath("$.data.lesson").value("另一条教训"));

        mockMvc.perform(get(BASE + "?status=APPROVED").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void rejectThenApproveConflicts() throws Exception {
        String token = tokenFor(500L);
        long id = createCandidate(token, "将被驳回", "教训");

        mockMvc.perform(post(BASE + "/" + id + "/reject").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.lessonNo").doesNotExist());

        mockMvc.perform(post(BASE + "/" + id + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post(BASE + "/" + id + "/reject").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void disableFlowAndMissingLesson() throws Exception {
        String token = tokenFor(500L);
        long id = createCandidate(token, "将被停用", "教训");

        // PENDING 不能直接停用。
        mockMvc.perform(post(BASE + "/" + id + "/disable").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());

        mockMvc.perform(post(BASE + "/" + id + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/" + id + "/disable").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.lessonNo").value(6));

        mockMvc.perform(post(BASE + "/" + id + "/disable").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());

        mockMvc.perform(get(BASE + "/999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE + "/999999/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createValidationAndBadStatusFail() throws Exception {
        String token = tokenFor(500L);

        mockMvc.perform(post(BASE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \",\"lesson\":\"教训\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(BASE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"只有标题\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(BASE + "?status=WHATEVER").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void extractTriggerReturnsSummary() throws Exception {
        when(extractionService.extractRecent(any())).thenReturn(new AdminAiExperienceExtractionResultDto(14, 12, 3, 5, 2));
        String token = tokenFor(500L);

        mockMvc.perform(post(BASE + "/extract")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"windowDays\":14,\"maxTurns\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windowDays").value(14))
                .andExpect(jsonPath("$.data.scannedTurns").value(12))
                .andExpect(jsonPath("$.data.extractionCalls").value(3))
                .andExpect(jsonPath("$.data.producedCandidates").value(5))
                .andExpect(jsonPath("$.data.failedTurns").value(2));

        // 无 body 也可触发（窗口/上限走服务层默认）。
        mockMvc.perform(post(BASE + "/extract").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.producedCandidates").value(5));
    }

    @Test
    void forbiddenWithoutAiModelPermissions() throws Exception {
        String token = tokenFor(100L);

        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"标题\",\"lesson\":\"教训\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/1/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/extract")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
