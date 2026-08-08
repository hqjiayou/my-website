package com.mywebsite.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@Sql(
        scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminReservationManagementMvcTest extends AbstractAdminSpringBootTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RESERVED_AT = "2026-08-10T10:00:00Z";

    @Autowired
    private MockMvc mockMvc;

    private String tokenFor(long userId) throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        return AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", userId);
    }

    @Test
    void happyPath_createListFilterGetStatusDelete() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token, "CAKE");

        ObjectNode create = MAPPER.createObjectNode();
        create.put("customerName", "山田太郎");
        create.put("phone", "09012345678");
        create.put("serviceType", "オイル交換");
        create.put("reservedAt", RESERVED_AT);
        create.put("note", "午前希望");

        MvcResult created = mockMvc.perform(post("/api/admin/sites/" + siteId + "/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerName").value("山田太郎"))
                .andExpect(jsonPath("$.data.phone").value("09012345678"))
                .andExpect(jsonPath("$.data.serviceType").value("オイル交換"))
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.data.note").value("午前希望"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn();

        long reservationId =
                MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        ObjectNode confirmed = MAPPER.createObjectNode();
        confirmed.put("customerName", "佐藤花子");
        confirmed.put("phone", "08011112222");
        confirmed.put("serviceType", "点検");
        confirmed.put("reservedAt", "2026-08-11T15:00:00Z");
        long confirmedId = createReservation(token, siteId, confirmed);
        patchStatus(token, siteId, confirmedId, "CONFIRMED", 0);

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/reservations")
                        .param("status", "NEW")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) reservationId));

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/reservations/" + reservationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.note").value("午前希望"));

        ObjectNode statusBody = MAPPER.createObjectNode();
        statusBody.put("status", "CONFIRMED");
        statusBody.put("version", 0);
        mockMvc.perform(patch("/api/admin/sites/" + siteId + "/reservations/" + reservationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(statusBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.version").value(1));

        statusBody.put("status", "DONE");
        statusBody.put("version", 1);
        mockMvc.perform(patch("/api/admin/sites/" + siteId + "/reservations/" + reservationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(statusBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));

        mockMvc.perform(delete("/api/admin/sites/" + siteId + "/reservations/" + reservationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/reservations/" + reservationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsIllegalStatusTransitionsAndTerminalStates() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token, "CAR_REPAIR");
        long reservationId = createNewReservation(token, siteId);

        // NEW → DONE 非法
        ObjectNode bad = MAPPER.createObjectNode();
        bad.put("status", "DONE");
        bad.put("version", 0);
        mockMvc.perform(patch("/api/admin/sites/" + siteId + "/reservations/" + reservationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());

        patchStatus(token, siteId, reservationId, "CANCELLED", 0);

        // 终态 CANCELLED 不可再迁
        ObjectNode revive = MAPPER.createObjectNode();
        revive.put("status", "CONFIRMED");
        revive.put("version", 1);
        mockMvc.perform(patch("/api/admin/sites/" + siteId + "/reservations/" + reservationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(revive)))
                .andExpect(status().isBadRequest());

        long doneId = createNewReservation(token, siteId);
        patchStatus(token, siteId, doneId, "CONFIRMED", 0);
        patchStatus(token, siteId, doneId, "DONE", 1);

        ObjectNode afterDone = MAPPER.createObjectNode();
        afterDone.put("status", "CANCELLED");
        afterDone.put("version", 2);
        mockMvc.perform(patch("/api/admin/sites/" + siteId + "/reservations/" + doneId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(afterDone)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createForbiddenWithoutPermission() throws Exception {
        String token = tokenFor(100L);
        long siteId = createSite(tokenFor(500L), "SALON");
        ObjectNode body = MAPPER.createObjectNode();
        body.put("customerName", "A");
        body.put("phone", "09000000000");
        body.put("serviceType", "cut");
        body.put("reservedAt", RESERVED_AT);
        mockMvc.perform(post("/api/admin/sites/" + siteId + "/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void optimisticConflictOnStaleVersion() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token, "CAKE");
        long reservationId = createNewReservation(token, siteId);

        ObjectNode statusBody = MAPPER.createObjectNode();
        statusBody.put("status", "CONFIRMED");
        statusBody.put("version", 0);
        mockMvc.perform(patch("/api/admin/sites/" + siteId + "/reservations/" + reservationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(statusBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        statusBody.put("status", "DONE");
        statusBody.put("version", 0);
        mockMvc.perform(patch("/api/admin/sites/" + siteId + "/reservations/" + reservationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(statusBody)))
                .andExpect(status().isConflict());
    }

    @Test
    void crossSiteReservationReturns404AndMissingSiteRejected() throws Exception {
        String token = tokenFor(500L);
        long siteA = createSite(token, "CAKE");
        long siteB = createSite(token, "SALON");
        long reservationId = createNewReservation(token, siteA);

        mockMvc.perform(get("/api/admin/sites/" + siteB + "/reservations/" + reservationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/admin/sites/" + siteB + "/reservations/" + reservationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        ObjectNode body = MAPPER.createObjectNode();
        body.put("customerName", "Ghost");
        body.put("phone", "09000000001");
        body.put("serviceType", "x");
        body.put("reservedAt", RESERVED_AT);
        mockMvc.perform(post("/api/admin/sites/999999/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void softDeleteSiteAlsoSoftDeletesReservations() throws Exception {
        String token = tokenFor(500L);
        long siteId = createSite(token, "CLINIC");
        long reservationId = createNewReservation(token, siteId);

        mockMvc.perform(delete("/api/admin/sites/" + siteId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/reservations/" + reservationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void worksAcrossSiteTypes() throws Exception {
        String token = tokenFor(500L);
        for (String siteType : new String[] {"CAR_REPAIR", "USED_CAR", "CAKE", "SALON", "CLINIC", "GYOSEISHOSHI"}) {
            long siteId = createSite(token, siteType);
            long reservationId = createNewReservation(token, siteId);
            mockMvc.perform(get("/api/admin/sites/" + siteId + "/reservations/" + reservationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("NEW"));
        }
    }

    private long createSite(String token, String siteType) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("siteCode", "r_" + Long.toHexString(System.nanoTime()));
        body.put("siteName", "Reservation Site");
        body.put("siteType", siteType);
        MvcResult created = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long createNewReservation(String token, long siteId) throws Exception {
        ObjectNode create = MAPPER.createObjectNode();
        create.put("customerName", "Customer");
        create.put("phone", "09099998888");
        create.put("serviceType", "general");
        create.put("reservedAt", RESERVED_AT);
        return createReservation(token, siteId, create);
    }

    private long createReservation(String token, long siteId, ObjectNode body) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admin/sites/" + siteId + "/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void patchStatus(String token, long siteId, long reservationId, String status, int version)
            throws Exception {
        ObjectNode statusBody = MAPPER.createObjectNode();
        statusBody.put("status", status);
        statusBody.put("version", version);
        mockMvc.perform(patch("/api/admin/sites/" + siteId + "/reservations/" + reservationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(statusBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(status));
    }
}
