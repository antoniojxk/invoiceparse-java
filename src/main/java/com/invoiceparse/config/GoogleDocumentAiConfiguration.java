package com.invoiceparse.config;

import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.DocumentProcessorServiceSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "invoiceparse.extraction-provider", havingValue = "google-document-ai")
public class GoogleDocumentAiConfiguration {
    @Bean(destroyMethod = "close")
    DocumentProcessorServiceClient documentProcessorServiceClient(DocumentAiProperties properties) throws IOException {
        properties.requireConfigured();
        var settings = DocumentProcessorServiceSettings.newBuilder()
                .setEndpoint(properties.endpoint())
                .build();
        return DocumentProcessorServiceClient.create(settings);
    }
}
