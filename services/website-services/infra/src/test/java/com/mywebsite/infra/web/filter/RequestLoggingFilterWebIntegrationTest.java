package com.mywebsite.infra.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.common.web.HttpRequestCorrelation;
import com.mywebsite.infra.itest.ProbeLoggingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = ProbeLoggingApplication.class)
@AutoConfigureMockMvc
class RequestLoggingFilterWebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void doesNotEchoInboundRequestIdOnServletResponse() throws Exception {
        // 入站已带 X-Request-Id 时由 Gateway 在响应完成时回写一次，Servlet 不再叠加。
        mockMvc.perform(MockMvcRequestBuilders.get("/infra-itest/ping")
                        .header(HttpRequestCorrelation.X_REQUEST_ID, "id-from-client"))
                .andExpect(r -> assertThat(r.getResponse().getHeader(HttpRequestCorrelation.X_REQUEST_ID))
                        .isNull());
    }

    @Test
    void generatesAndEchoesMissingRequestId() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/infra-itest/ping"))
                .andExpect(r -> assertThat(r.getResponse().getHeader(HttpRequestCorrelation.X_REQUEST_ID))
                        .isNotBlank());
    }
}
