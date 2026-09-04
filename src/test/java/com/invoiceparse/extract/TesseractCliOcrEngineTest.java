package com.invoiceparse.extract;

import com.invoiceparse.config.InvoiceParseProperties;
import com.invoiceparse.exception.DocumentProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TesseractCliOcrEngineTest {
    @TempDir Path tempDir;

    @Test void terminatesOcrProcessAtConfiguredTimeout() throws Exception {
        Path slowCommand = tempDir.resolve("slow-ocr.sh");
        Files.writeString(slowCommand, "#!/bin/sh\nsleep 10\n");
        assertThat(slowCommand.toFile().setExecutable(true)).isTrue();
        var properties = new InvoiceParseProperties(40, 250, slowCommand.toString(), "eng", 1, .7, .1);
        var engine = new TesseractCliOcrEngine(properties);

        assertThatThrownBy(() -> engine.recognize(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), 1))
                .isInstanceOf(DocumentProcessingException.class)
                .satisfies(error -> assertThat(((DocumentProcessingException) error).getCode()).isEqualTo("OCR_TIMEOUT"));
    }
}
