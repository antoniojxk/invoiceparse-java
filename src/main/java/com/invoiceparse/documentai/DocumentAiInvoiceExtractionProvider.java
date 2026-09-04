package com.invoiceparse.documentai;

import com.invoiceparse.config.DocumentAiProperties;
import com.invoiceparse.extract.DetectedFileType;
import com.invoiceparse.extract.DocumentInputInspector;
import com.invoiceparse.extract.InvoiceExtractionProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "invoiceparse.extraction-provider", havingValue = "google-document-ai")
public class DocumentAiInvoiceExtractionProvider implements InvoiceExtractionProvider {
    private final DocumentInputInspector inspector;
    private final DocumentAiGateway gateway;
    private final DocumentAiInvoiceMapper mapper;
    private final DocumentAiProperties properties;

    public DocumentAiInvoiceExtractionProvider(DocumentInputInspector inspector, DocumentAiGateway gateway,
                                               DocumentAiInvoiceMapper mapper, DocumentAiProperties properties) {
        this.inspector = inspector;
        this.gateway = gateway;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public ExtractionResult extract(byte[] bytes, DetectedFileType fileType) {
        var metadata = inspector.inspect(bytes, fileType);
        var document = gateway.process(bytes, metadata.mimeType());
        return mapper.map(document, metadata);
    }

    @Override
    public String cacheNamespace() {
        return "google-document-ai:" + properties.processorName();
    }
}
