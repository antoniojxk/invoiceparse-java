package com.invoiceparse.extract;

import com.invoiceparse.config.InvoiceParseProperties;
import com.invoiceparse.exception.DocumentProcessingException;
import com.invoiceparse.model.TextToken;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@Component
public class TesseractCliOcrEngine implements OcrEngine {
    private final InvoiceParseProperties properties;
    public TesseractCliOcrEngine(InvoiceParseProperties properties) { this.properties = properties; }

    @Override
    public OcrResult recognize(BufferedImage image, int page) {
        Path imageFile = null;
        Path outputFile = null;
        Path errorFile = null;
        Process process = null;
        try {
            imageFile = Files.createTempFile("invoiceparse-", ".png");
            outputFile = Files.createTempFile("invoiceparse-ocr-", ".tsv");
            errorFile = Files.createTempFile("invoiceparse-ocr-", ".log");
            if (!ImageIO.write(image, "png", imageFile.toFile())) {
                throw new DocumentProcessingException("OCR_FAILED", "The image could not be prepared for OCR");
            }
            process = new ProcessBuilder(properties.tesseractCommand(), imageFile.toString(), "stdout",
                    "-l", properties.tesseractLanguage(), "--psm", "6", "tsv")
                    .redirectOutput(outputFile.toFile()).redirectError(errorFile.toFile()).start();
            if (!process.waitFor(properties.ocrTimeoutSeconds(), TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
                throw new DocumentProcessingException("OCR_TIMEOUT",
                        "Tesseract OCR exceeded the " + properties.ocrTimeoutSeconds() + " second limit");
            }
            String output = Files.readString(outputFile);
            if (process.exitValue() != 0) {
                throw new DocumentProcessingException("OCR_FAILED", "Tesseract OCR failed: " + safeMessage(Files.readString(errorFile)));
            }
            return parseTsv(output, page);
        } catch (IOException e) {
            throw new DocumentProcessingException("OCR_FAILED", "Tesseract is unavailable or could not read the image", e);
        } catch (InterruptedException e) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new DocumentProcessingException("OCR_FAILED", "OCR was interrupted", e);
        } finally {
            deleteQuietly(imageFile);
            deleteQuietly(outputFile);
            deleteQuietly(errorFile);
        }
    }

    OcrResult parseTsv(String tsv, int page) {
        var tokens = new ArrayList<TextToken>();
        var lines = new ArrayList<String>();
        int priorBlock = -1, priorParagraph = -1, priorLine = -1;
        var current = new StringBuilder();
        for (String row : tsv.split("\\R")) {
            String[] c = row.split("\\t", 12);
            if (c.length < 12 || !"5".equals(c[0]) || c[11].isBlank()) continue;
            try {
                int block = Integer.parseInt(c[2]), paragraph = Integer.parseInt(c[3]), line = Integer.parseInt(c[4]);
                if (priorLine != -1 && (block != priorBlock || paragraph != priorParagraph || line != priorLine)) {
                    lines.add(current.toString().trim()); current.setLength(0);
                }
                if (!current.isEmpty()) current.append(' ');
                current.append(c[11]);
                double confidence = Math.max(0, Double.parseDouble(c[10])) / 100.0;
                tokens.add(new TextToken(c[11], page, Float.parseFloat(c[6]), Float.parseFloat(c[7]),
                        Float.parseFloat(c[8]), Float.parseFloat(c[9]), confidence));
                priorBlock = block; priorParagraph = paragraph; priorLine = line;
            } catch (NumberFormatException ignored) { }
        }
        if (!current.isEmpty()) lines.add(current.toString().trim());
        return new OcrResult(String.join("\n", lines), tokens);
    }

    private String safeMessage(String value) {
        String oneLine = value == null ? "unknown error" : value.replaceAll("\\s+", " ").trim();
        return oneLine.substring(0, Math.min(oneLine.length(), 200));
    }

    private void deleteQuietly(Path path) {
        if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }
}
