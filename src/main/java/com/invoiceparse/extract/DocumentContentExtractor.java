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
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
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
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) throw new DocumentProcessingException("INVALID_FILE", "The PDF has no pages");
            if (pageCount > properties.maximumPdfPages()) {
                throw new DocumentProcessingException("DOCUMENT_LIMIT_EXCEEDED",
                        "The PDF exceeds the " + properties.maximumPdfPages() + " page safety limit");
            }
            for (int i = 0; i < pageCount; i++) validatePdfPageDimensions(document.getPage(i).getCropBox().getWidth(),
                    document.getPage(i).getCropBox().getHeight());
            var stripper = new PdfTextExtractor();
            String text = stripper.extract(document);
            long usable = text.chars().filter(Character::isLetterOrDigit).count();
            int threshold = properties.minimumTextCharactersPerPage() * pageCount;
            if (usable >= threshold) return new ExtractedContent(text, stripper.tokens(), SourceType.DIGITAL_PDF, pageCount);

            var renderer = new PDFRenderer(document);
            var tokens = new ArrayList<TextToken>();
            var pages = new ArrayList<String>();
            for (int i = 0; i < pageCount; i++) {
                var result = ocr.recognize(renderer.renderImageWithDPI(i, properties.pdfRenderDpi(), ImageType.RGB), i + 1);
                pages.add(result.text()); tokens.addAll(result.tokens());
            }
            return new ExtractedContent(String.join("\n", pages), tokens, SourceType.SCANNED_PDF, pageCount);
        } catch (DocumentProcessingException e) { throw e; }
        catch (IOException e) { throw new DocumentProcessingException("INVALID_FILE", "The PDF is corrupt or unreadable", e); }
    }

    private ExtractedContent extractImage(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new DocumentProcessingException("INVALID_FILE", "The image is corrupt or unreadable");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new DocumentProcessingException("INVALID_FILE", "The image is corrupt or unreadable");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                validateImageDimensions(reader.getWidth(0), reader.getHeight(0));
                var image = reader.read(0);
                if (image == null) throw new DocumentProcessingException("INVALID_FILE", "The image is corrupt or unreadable");
                var result = ocr.recognize(image, 1);
                return new ExtractedContent(result.text(), result.tokens(), SourceType.IMAGE, 1);
            } finally {
                reader.dispose();
            }
        } catch (DocumentProcessingException e) { throw e; }
        catch (IOException e) { throw new DocumentProcessingException("INVALID_FILE", "The image is corrupt or unreadable", e); }
    }

    private void validatePdfPageDimensions(float widthPoints, float heightPoints) {
        int width = (int) Math.ceil(widthPoints * properties.pdfRenderDpi() / 72.0);
        int height = (int) Math.ceil(heightPoints * properties.pdfRenderDpi() / 72.0);
        validateDimensions(width, height, "PDF page");
    }

    private void validateImageDimensions(int width, int height) {
        validateDimensions(width, height, "image");
    }

    private void validateDimensions(int width, int height, String label) {
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0 || width > properties.maximumImageDimension()
                || height > properties.maximumImageDimension() || pixels > properties.maximumImagePixels()) {
            throw new DocumentProcessingException("DOCUMENT_LIMIT_EXCEEDED",
                    "The " + label + " dimensions exceed the configured safety limit");
        }
    }
}
