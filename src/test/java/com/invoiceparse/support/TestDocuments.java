package com.invoiceparse.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class TestDocuments {
    private TestDocuments() { }
    public static byte[] pdf(String... lines) {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage(PDRectangle.A4); document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                stream.newLineAtOffset(50, 790);
                for (String line : lines) { stream.showText(line); stream.newLineAtOffset(0, -18); }
                stream.endText();
            }
            document.save(output); return output.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    public static byte[] blankPdf() { return pdf(""); }

    public static byte[] blankPdf(int pageCount) {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pageCount; i++) document.addPage(new PDPage(PDRectangle.A4));
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
