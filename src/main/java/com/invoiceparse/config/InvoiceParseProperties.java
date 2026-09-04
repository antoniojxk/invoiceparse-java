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
        double totalTolerance,
        int maximumPdfPages,
        long maximumImagePixels,
        int maximumImageDimension,
        long resultRetentionMinutes,
        DemoAccess demoAccess
) {
    public record DemoAccess(
            boolean enabled,
            int windowSeconds,
            int maxRequestsPerClient,
            int maxRequestsGlobal,
            int maxConcurrentRequests
    ) {
        public DemoAccess {
            if (windowSeconds <= 0) windowSeconds = 600;
            if (maxRequestsPerClient <= 0) maxRequestsPerClient = 5;
            if (maxRequestsGlobal <= 0) maxRequestsGlobal = 30;
            if (maxConcurrentRequests <= 0) maxConcurrentRequests = 1;
        }
    }

    public InvoiceParseProperties {
        if (minimumTextCharactersPerPage <= 0) minimumTextCharactersPerPage = 40;
        if (pdfRenderDpi <= 0) pdfRenderDpi = 250;
        if (tesseractCommand == null || tesseractCommand.isBlank()) tesseractCommand = "tesseract";
        if (tesseractLanguage == null || tesseractLanguage.isBlank()) tesseractLanguage = "eng";
        if (ocrTimeoutSeconds <= 0) ocrTimeoutSeconds = 60;
        if (minimumOverallConfidence <= 0) minimumOverallConfidence = 0.70;
        if (totalTolerance <= 0) totalTolerance = 0.10;
        if (maximumPdfPages <= 0) maximumPdfPages = 25;
        if (maximumImagePixels <= 0) maximumImagePixels = 40_000_000;
        if (maximumImageDimension <= 0) maximumImageDimension = 12_000;
        if (resultRetentionMinutes < 0) resultRetentionMinutes = 0;
        if (demoAccess == null) demoAccess = new DemoAccess(false, 600, 5, 30, 1);
    }
}
