package com.invoiceparse.documentai;

import com.google.api.gax.rpc.ApiException;
import com.google.cloud.documentai.v1.Document;
import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.ProcessRequest;
import com.google.cloud.documentai.v1.RawDocument;
import com.google.protobuf.ByteString;
import com.invoiceparse.config.DocumentAiProperties;
import com.invoiceparse.exception.DocumentProcessingException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "invoiceparse.extraction-provider", havingValue = "google-document-ai")
public class GoogleDocumentAiGateway implements DocumentAiGateway {
    private final DocumentProcessorServiceClient client;
    private final DocumentAiProperties properties;

    public GoogleDocumentAiGateway(DocumentProcessorServiceClient client, DocumentAiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Document process(byte[] content, String mimeType) {
        var rawDocument = RawDocument.newBuilder()
                .setContent(ByteString.copyFrom(content))
                .setMimeType(mimeType)
                .build();
        var request = ProcessRequest.newBuilder()
                .setName(properties.processorName())
                .setRawDocument(rawDocument)
                .build();
        try {
            return client.processDocument(request).getDocument();
        } catch (ApiException e) {
            throw new DocumentProcessingException("DOCUMENT_AI_ERROR",
                    "Google Document AI could not process the document: " + e.getStatusCode().getCode(), e);
        }
    }
}
