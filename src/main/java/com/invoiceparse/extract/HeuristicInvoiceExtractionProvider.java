package com.invoiceparse.extract;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "invoiceparse.extraction-provider", havingValue = "heuristic", matchIfMissing = true)
public class HeuristicInvoiceExtractionProvider implements InvoiceExtractionProvider {
    private final DocumentContentExtractor contentExtractor;
    private final InvoiceFieldExtractor invoiceExtractor;

    public HeuristicInvoiceExtractionProvider(DocumentContentExtractor contentExtractor,
                                               InvoiceFieldExtractor invoiceExtractor) {
        this.contentExtractor = contentExtractor;
        this.invoiceExtractor = invoiceExtractor;
    }

    @Override
    public ExtractionResult extract(byte[] bytes, DetectedFileType fileType) {
        var content = contentExtractor.extract(bytes, fileType);
        return new ExtractionResult(invoiceExtractor.extract(content), content);
    }
}
