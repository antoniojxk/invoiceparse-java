package com.invoiceparse.extract;

import com.invoiceparse.config.InvoiceParseProperties;
import com.invoiceparse.exception.DocumentProcessingException;
import com.invoiceparse.model.SourceType;
import com.invoiceparse.support.TestDocuments;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentContentExtractorTest {
    private final InvoiceParseProperties properties = propertiesWithLimits(25, 40_000_000, 12_000);

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

    @Test void rejectsPdfAboveConfiguredPageLimitBeforeOcr() {
        OcrEngine mustNotRun = (image, page) -> { throw new AssertionError("OCR should not run"); };
        var limited = propertiesWithLimits(1, 40_000_000, 12_000);

        assertThatThrownBy(() -> new DocumentContentExtractor(mustNotRun, limited)
                .extract(TestDocuments.blankPdf(2), DetectedFileType.PDF))
                .isInstanceOf(DocumentProcessingException.class)
                .satisfies(error -> assertThat(((DocumentProcessingException) error).getCode())
                        .isEqualTo("DOCUMENT_LIMIT_EXCEEDED"));
    }

    @Test void rejectsOversizedRasterBeforeDecodingForOcr() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
        OcrEngine mustNotRun = (image, page) -> { throw new AssertionError("OCR should not run"); };
        var limited = propertiesWithLimits(3, 100, 100);

        assertThatThrownBy(() -> new DocumentContentExtractor(mustNotRun, limited)
                .extract(output.toByteArray(), DetectedFileType.PNG))
                .isInstanceOf(DocumentProcessingException.class)
                .satisfies(error -> assertThat(((DocumentProcessingException) error).getCode())
                        .isEqualTo("DOCUMENT_LIMIT_EXCEEDED"));
    }

    private InvoiceParseProperties propertiesWithLimits(int pages, long pixels, int dimension) {
        return new InvoiceParseProperties(20, 72, "tesseract", "eng", 60, .7, .1,
                pages, pixels, dimension, 0,
                new InvoiceParseProperties.DemoAccess(false, 600, 5, 30, 1));
    }
}
