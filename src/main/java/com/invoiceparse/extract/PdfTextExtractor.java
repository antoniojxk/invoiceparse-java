package com.invoiceparse.extract;

import com.invoiceparse.model.TextToken;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class PdfTextExtractor extends PDFTextStripper {
    private final List<TextToken> tokens = new ArrayList<>();
    public PdfTextExtractor() throws IOException { setSortByPosition(true); }

    @Override
    protected void writeString(String text, List<TextPosition> positions) throws IOException {
        for (TextPosition p : positions) {
            String unicode = p.getUnicode();
            if (unicode != null && !unicode.isBlank()) {
                tokens.add(new TextToken(unicode, getCurrentPageNo(), p.getXDirAdj(), p.getYDirAdj(),
                        p.getWidthDirAdj(), p.getHeightDir(), 1.0));
            }
        }
        super.writeString(text, positions);
    }

    public String extract(org.apache.pdfbox.pdmodel.PDDocument document) throws IOException { return getText(document); }
    public List<TextToken> tokens() { return List.copyOf(tokens); }
}
