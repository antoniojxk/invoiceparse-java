package com.invoiceparse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("invoiceparse")
public record InvoiceParseProperties(
        int minimumTextCharactersPerPage,
        int pdfRenderDpi,
        String tesseractCommand,
        String tesseractLanguage,
        int ocrTimeoutSeconds,
        double minimumOverallConfidence,
        double totalTolerance
) {
    public InvoiceParseProperties {
        if (minimumTextCharactersPerPage <= 0) minimumTextCharactersPerPage = 40;
        if (pdfRenderDpi <= 0) pdfRenderDpi = 250;
        if (tesseractCommand == null || tesseractCommand.isBlank()) tesseractCommand = "tesseract";
        if (tesseractLanguage == null || tesseractLanguage.isBlank()) tesseractLanguage = "eng";
        if (ocrTimeoutSeconds <= 0) ocrTimeoutSeconds = 60;
        if (minimumOverallConfidence <= 0) minimumOverallConfidence = 0.70;
        if (totalTolerance <= 0) totalTolerance = 0.10;
    }
}
