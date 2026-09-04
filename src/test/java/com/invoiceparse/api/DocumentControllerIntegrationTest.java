package com.invoiceparse.api;

import com.invoiceparse.persistence.DocumentRecordRepository;
import com.invoiceparse.support.TestDocuments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired DocumentRecordRepository repository;

    @BeforeEach void clean() { repository.deleteAll(); }

    @Test void parsesDigitalPdfAndDetectsDuplicate() throws Exception {
        byte[] pdf = TestDocuments.pdf(
                "TAX INVOICE - SYNTHETIC SAMPLE",
                "Supplier: Example Components Pvt Ltd",
                "Supplier GSTIN: 27ABCDE1234F1Z5",
                "Customer: Demo Retail LLP",
                "Customer GSTIN: 29PQRSX5678K1Z2",
                "Invoice No: SI-2026-104",
                "Invoice Date: 07-Aug-2026",
                "Description | HSN/SAC | Qty | Unit | Rate | GST % | Taxable Amount | Total",
                "Copper Cable | 8544 | 2 | roll | 500.00 | 18 | 1000.00 | 1000.00",
                "Taxable Amount: 1000.00", "CGST: 90.00", "SGST: 90.00", "Grand Total: 1180.00");
        var file = new MockMultipartFile("file", "sample.pdf", "application/pdf", pdf);
        mvc.perform(multipart("/api/v1/documents/parse").file(file))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.sourceType").value("DIGITAL_PDF"))
                .andExpect(jsonPath("$.invoiceNumber").value("SI-2026-104"))
                .andExpect(jsonPath("$.lineItems.length()").value(1))
                .andExpect(jsonPath("$.grandTotal").value(1180.00));
        mvc.perform(multipart("/api/v1/documents/parse").file(file))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.invoiceNumber").value("SI-2026-104"));
    }

    @Test void returnsConsistentErrorForUnsupportedFile() throws Exception {
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        mvc.perform(multipart("/api/v1/documents/parse").file(file))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"))
                .andExpect(jsonPath("$.path").value("/api/v1/documents/parse"));
    }

    @Test void rejectsMissingFilePart() throws Exception {
        mvc.perform(multipart("/api/v1/documents/parse"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_FILE"));
    }
}
