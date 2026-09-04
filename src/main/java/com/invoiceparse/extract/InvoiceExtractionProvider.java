package com.invoiceparse.extract;

import com.invoiceparse.model.ExtractedContent;

public interface InvoiceExtractionProvider {
    ExtractionResult extract(byte[] bytes, DetectedFileType fileType);

    default String cacheNamespace() {
        return "heuristic";
    }

    record ExtractionResult(ParsedInvoice invoice, ExtractedContent content) { }
}
