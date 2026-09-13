package com.invoiceparse.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "invoiceparse.frontend-origins=https://invoiceparse.nuvorima.com")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class FrontendCorsIntegrationTest {
    private static final String FRONTEND = "https://invoiceparse.nuvorima.com";
    @Autowired
    MockMvc mvc;

    @Test
    void allowsFrontendHealthAndParsePreflight() throws Exception {
        mvc.perform(get("/actuator/health").header("Origin", FRONTEND))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(options("/api/v1/documents/parse").header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));
    }

    @Test
    void exposesValidationErrorsToFrontend() throws Exception {
        var invalid = new MockMultipartFile("file", "invalid.txt", "text/plain", "invalid".getBytes());
        mvc.perform(multipart("/api/v1/documents/parse").file(invalid).header("Origin", FRONTEND))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void rejectsUnknownOriginsAndMethods() throws Exception {
        mvc.perform(get("/actuator/health").header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(options("/api/v1/documents/parse").header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void keepsSameOriginRequestsWorking() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
