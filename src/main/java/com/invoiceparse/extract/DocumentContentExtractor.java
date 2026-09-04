package com.invoiceparse.extract;

import com.invoiceparse.config.InvoiceParseProperties;
import com.invoiceparse.exception.DocumentProcessingException;
import com.invoiceparse.model.ExtractedContent;
import com.invoiceparse.model.SourceType;
import com.invoiceparse.model.TextToken;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;

@Component
public class DocumentContentExtractor {
    private final OcrEngine ocr;
    private final InvoiceParseProperties properties;
    public DocumentContentExtractor(OcrEngine ocr, InvoiceParseProperties properties) { this.ocr = ocr; this.properties = properties; }

    public ExtractedContent extract(byte[] bytes, DetectedFileType type) {
        return type == DetectedFileType.PDF ? extractPdf(bytes) : extractImage(bytes);
    }

    private ExtractedContent extractPdf(byte[] bytes) {
        try (var document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() == 0) throw new DocumentProcessingException("INVALID_FILE", "The PDF has no pages");
            var stripper = new PdfTextExtractor();
            String text = stripper.extract(document);
            long usable = text.chars().filter(Character::isLetterOrDigit).count();
            int threshold = properties.minimumTextCharactersPerPage() * document.getNumberOfPages();
            if (usable >= threshold) return new ExtractedContent(text, stripper.tokens(), SourceType.DIGITAL_PDF, document.getNumberOfPages());

            var renderer = new PDFRenderer(document);
            var tokens = new ArrayList<TextToken>();
            var pages = new ArrayList<String>();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                var result = ocr.recognize(renderer.renderImageWithDPI(i, properties.pdfRenderDpi(), ImageType.RGB), i + 1);
                pages.add(result.text()); tokens.addAll(result.tokens());
            }
            return new ExtractedContent(String.join("\n", pages), tokens, SourceType.SCANNED_PDF, document.getNumberOfPages());
        } catch (DocumentProcessingException e) { throw e; }
        catch (IOException e) { throw new DocumentProcessingException("INVALID_FILE", "The PDF is corrupt or unreadable", e); }
    }

    private ExtractedContent extractImage(byte[] bytes) {
        try {
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw new DocumentProcessingException("INVALID_FILE", "The image is corrupt or unreadable");
            var result = ocr.recognize(image, 1);
            return new ExtractedContent(result.text(), result.tokens(), SourceType.IMAGE, 1);
        } catch (IOException e) { throw new DocumentProcessingException("INVALID_FILE", "The image is corrupt or unreadable", e); }
    }
}
