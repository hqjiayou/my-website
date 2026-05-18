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
    void echoesProvidedRequestId() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/infra-itest/ping")
                        .header(HttpRequestCorrelation.X_REQUEST_ID, "id-from-client"))
                .andExpect(r -> assertThat(r.getResponse().getHeader(HttpRequestCorrelation.X_REQUEST_ID))
                        .isEqualTo("id-from-client"));
    }

    @Test
    void generatesAndEchoesMissingRequestId() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/infra-itest/ping"))
                .andExpect(r -> assertThat(r.getResponse().getHeader(HttpRequestCorrelation.X_REQUEST_ID))
                        .isNotBlank());
    }
}
