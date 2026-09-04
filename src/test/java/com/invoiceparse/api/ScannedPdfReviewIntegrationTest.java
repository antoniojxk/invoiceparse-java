package com.invoiceparse.api;

import com.invoiceparse.extract.OcrEngine;
import com.invoiceparse.model.TextToken;
import com.invoiceparse.persistence.DocumentRecordRepository;
import com.invoiceparse.support.TestDocuments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ScannedPdfReviewIntegrationTest.HighConfidenceOcrConfiguration.class)
class ScannedPdfReviewIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired DocumentRecordRepository repository;

    @BeforeEach void clean() { repository.deleteAll(); }

    @Test void highOcrConfidenceCannotHideMissingGstinsAndBadTotal() throws Exception {
        var file = new MockMultipartFile("file", "review-case.pdf", "application/pdf", TestDocuments.blankPdf());

        mvc.perform(multipart("/api/v1/documents/parse").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("SCANNED_PDF"))
                .andExpect(jsonPath("$.supplierGstin").doesNotExist())
                .andExpect(jsonPath("$.customerGstin").doesNotExist())
                .andExpect(jsonPath("$.fieldConfidences.supplierGstin").value(0.0))
                .andExpect(jsonPath("$.fieldConfidences.customerGstin").value(0.0))
                .andExpect(jsonPath("$.validationResults[?(@.code == 'INVOICE_TOTAL')].valid").value(false))
                .andExpect(jsonPath("$.overallConfidence").value(0.53))
                .andExpect(jsonPath("$.manualReviewRequired").value(true))
                .andExpect(jsonPath("$.warnings", hasItem("Supplier GSTIN was expected but could not be extracted")))
                .andExpect(jsonPath("$.warnings", hasItem("Customer GSTIN was expected but could not be extracted")))
                .andExpect(jsonPath("$.warnings", hasItem("Grand total does not match the extracted components (grandTotal)")));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HighConfidenceOcrConfiguration {
        @Bean @Primary
        OcrEngine highConfidenceButWrongOcr() {
            String text = """
                    NORTHWIND WHOLESALE
                    GST INVOICE
                    Supplier GSTIN: 1SABCDE1234F1Z7
                    Customer: Sample Office Supplies
                    Customer GSTIM: 2SPQRSX5678K1Z2
                    Bill No: PI-7781
                    Date: 18/08/2026
                    Product Description Quantity Rate Amount
                    Paper Ream A4 5 240.00 1200.00
                    Marker Pack 2 150.00 300.00
                    Desk Organizer 1 450.00 450.00
                    Subtotal: 1950.00
                    IGST: 351.60
                    Grand Total: INR 2301.00
                    """;
            return (image, page) -> new OcrEngine.OcrResult(text,
                    List.of(new TextToken("invoice", page, 0, 0, 10, 10, .96)));
        }
    }
}
