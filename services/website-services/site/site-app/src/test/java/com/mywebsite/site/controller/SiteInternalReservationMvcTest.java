package com.mywebsite.site.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.agent.AgentHttpHeaders;
import com.mywebsite.site.testsupport.AbstractSiteSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 业务工具创建预约：字段不合法必须是 400，不能落到未处理异常的 500。 */
@AutoConfigureMockMvc
class SiteInternalReservationMvcTest extends AbstractSiteSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blankPhoneIsBadRequestWithFieldMessage() throws Exception {
        mockMvc.perform(post("/internal/site/v1/sites/10/reservations")
                        .header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, TEST_AGENT_INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"customerName":"Tanaka","phone":"","serviceType":"cut","reservedAt":"2026-09-02T10:00:00+09:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value(containsString("phone")));
    }

    @Test
    void unreadableReservedAtIsBadRequestWithoutEchoingTheValue() throws Exception {
        mockMvc.perform(post("/internal/site/v1/sites/10/reservations")
                        .header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, TEST_AGENT_INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"customerName":"Tanaka","phone":"090","serviceType":"cut","reservedAt":"not-a-date"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value(not(containsString("not-a-date"))));
    }
}
