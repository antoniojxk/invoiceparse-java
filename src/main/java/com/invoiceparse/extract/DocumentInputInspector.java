package com.invoiceparse.extract;

import com.invoiceparse.config.InvoiceParseProperties;
import com.invoiceparse.exception.DocumentProcessingException;
import com.invoiceparse.model.SourceType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
public class DocumentInputInspector {
    private final InvoiceParseProperties properties;

    public DocumentInputInspector(InvoiceParseProperties properties) {
        this.properties = properties;
    }

    public Metadata inspect(byte[] bytes, DetectedFileType type) {
        return type == DetectedFileType.PDF ? inspectPdf(bytes) : inspectImage(bytes, type);
    }

    private Metadata inspectPdf(byte[] bytes) {
        try (var document = Loader.loadPDF(bytes)) {
            int pages = document.getNumberOfPages();
            if (pages == 0) throw new DocumentProcessingException("INVALID_FILE", "The PDF has no pages");
            if (pages > properties.maximumPdfPages()) {
                throw new DocumentProcessingException("DOCUMENT_LIMIT_EXCEEDED",
                        "The PDF exceeds the " + properties.maximumPdfPages() + " page safety limit");
            }
            for (int i = 0; i < pages; i++) {
                var box = document.getPage(i).getCropBox();
                validateDimensions((int) Math.ceil(box.getWidth() * properties.pdfRenderDpi() / 72.0),
                        (int) Math.ceil(box.getHeight() * properties.pdfRenderDpi() / 72.0), "PDF page");
            }
            String text = new PDFTextStripper().getText(document);
            long usableCharacters = text.chars().filter(Character::isLetterOrDigit).count();
            SourceType source = usableCharacters >= (long) properties.minimumTextCharactersPerPage() * pages
                    ? SourceType.DIGITAL_PDF : SourceType.SCANNED_PDF;
            return new Metadata("application/pdf", source, pages);
        } catch (DocumentProcessingException e) {
            throw e;
        } catch (IOException e) {
            throw new DocumentProcessingException("INVALID_FILE", "The PDF is corrupt or unreadable", e);
        }
    }

    private Metadata inspectImage(byte[] bytes, DetectedFileType type) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new DocumentProcessingException("INVALID_FILE", "The image is corrupt or unreadable");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new DocumentProcessingException("INVALID_FILE", "The image is corrupt or unreadable");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                validateDimensions(reader.getWidth(0), reader.getHeight(0), "image");
            } finally {
                reader.dispose();
            }
            String mimeType = type == DetectedFileType.PNG ? "image/png" : "image/jpeg";
            return new Metadata(mimeType, SourceType.IMAGE, 1);
        } catch (DocumentProcessingException e) {
            throw e;
        } catch (IOException e) {
            throw new DocumentProcessingException("INVALID_FILE", "The image is corrupt or unreadable", e);
        }
    }

    private void validateDimensions(int width, int height, String label) {
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0 || width > properties.maximumImageDimension()
                || height > properties.maximumImageDimension() || pixels > properties.maximumImagePixels()) {
            throw new DocumentProcessingException("DOCUMENT_LIMIT_EXCEEDED",
                    "The " + label + " dimensions exceed the configured safety limit");
        }
    }

    public record Metadata(String mimeType, SourceType sourceType, int pageCount) { }
}
