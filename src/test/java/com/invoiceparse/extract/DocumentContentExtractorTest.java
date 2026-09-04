package com.invoiceparse.extract;

import com.invoiceparse.config.InvoiceParseProperties;
import com.invoiceparse.model.SourceType;
import com.invoiceparse.support.TestDocuments;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentContentExtractorTest {
    private final InvoiceParseProperties properties = new InvoiceParseProperties(20, 72, "tesseract", "eng", 60, .7, .1);

    @Test void usesTextLayerForDigitalPdf() {
        OcrEngine mustNotRun = (image, page) -> { throw new AssertionError("OCR should not run"); };
        var extractor = new DocumentContentExtractor(mustNotRun, properties);
        var result = extractor.extract(TestDocuments.pdf("TAX INVOICE WITH A USABLE TEXT LAYER", "Invoice No: INV-42"), DetectedFileType.PDF);
        assertThat(result.sourceType()).isEqualTo(SourceType.DIGITAL_PDF);
        assertThat(result.text()).contains("INV-42");
        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.tokens()).isNotEmpty();
    }

    @Test void fallsBackToOcrForScannedPdf() {
        OcrEngine stub = (image, page) -> new OcrEngine.OcrResult("Invoice No: OCR-7", List.of());
        var result = new DocumentContentExtractor(stub, properties).extract(TestDocuments.blankPdf(), DetectedFileType.PDF);
        assertThat(result.sourceType()).isEqualTo(SourceType.SCANNED_PDF);
        assertThat(result.text()).contains("OCR-7");
    }
}
