package com.invoiceparse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("invoiceparse.document-ai")
public record DocumentAiProperties(
        String projectId,
        String location,
        String processorId,
        String processorVersion
) {
    public DocumentAiProperties {
        if (location == null || location.isBlank()) location = "us";
        if (processorVersion == null) processorVersion = "";
    }

    public void requireConfigured() {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLOUD_PROJECT must be set when Google Document AI extraction is enabled");
        }
        if (processorId == null || processorId.isBlank()) {
            throw new IllegalStateException("DOCUMENT_AI_PROCESSOR_ID must be set when Google Document AI extraction is enabled");
        }
    }

    public String endpoint() {
        return location + "-documentai.googleapis.com:443";
    }

    public String processorName() {
        String base = "projects/%s/locations/%s/processors/%s".formatted(projectId, location, processorId);
        return processorVersion.isBlank() ? base : base + "/processorVersions/" + processorVersion;
    }
}
