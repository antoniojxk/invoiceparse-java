package com.invoiceparse.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DemoProfileIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void servesSyntheticSampleWithDemoSecurityHeaders() throws Exception {
        mvc.perform(get("/samples/digital-invoice-layout-a.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));

        mvc.perform(get("/samples/image-invoice-layout-b.png"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test void disablesPublicApiDocumentation() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    @Test void rateLimitsRepeatedParseAttemptsAndPreventsResponseCaching() throws Exception {
        var invalid = new MockMultipartFile("file", "invalid.txt", "text/plain", "not an invoice".getBytes());
        for (int i = 0; i < 5; i++) {
            mvc.perform(multipart("/api/v1/documents/parse").file(invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        mvc.perform(multipart("/api/v1/documents/parse").file(invalid))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
