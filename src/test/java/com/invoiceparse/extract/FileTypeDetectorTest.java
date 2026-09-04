package com.invoiceparse.extract;

import com.invoiceparse.exception.DocumentProcessingException;
import com.invoiceparse.support.TestDocuments;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FileTypeDetectorTest {
    private final FileTypeDetector detector = new FileTypeDetector();
    @Test void detectsPdfByContentNotFilename() { assertThat(detector.detect(TestDocuments.pdf("invoice"))).isEqualTo(DetectedFileType.PDF); }
    @Test void detectsPngAndJpegSignatures() {
        assertThat(detector.detect(new byte[]{(byte) 0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a})).isEqualTo(DetectedFileType.PNG);
        assertThat(detector.detect(new byte[]{(byte) 0xff,(byte) 0xd8,(byte) 0xff,0x00})).isEqualTo(DetectedFileType.JPEG);
    }
    @Test void rejectsUnsupportedContent() {
        assertThatThrownBy(() -> detector.detect("not a document".getBytes()))
                .isInstanceOf(DocumentProcessingException.class).hasMessageContaining("Only PDF");
    }
}
