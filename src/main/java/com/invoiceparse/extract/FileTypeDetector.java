package com.invoiceparse.extract;

import com.invoiceparse.exception.DocumentProcessingException;
import org.springframework.stereotype.Component;

@Component
public class FileTypeDetector {
    public DetectedFileType detect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new DocumentProcessingException("INVALID_FILE", "The uploaded file is empty");
        if (starts(bytes, 0x25, 0x50, 0x44, 0x46, 0x2D)) return DetectedFileType.PDF;
        if (starts(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return DetectedFileType.PNG;
        if (starts(bytes, 0xFF, 0xD8, 0xFF)) return DetectedFileType.JPEG;
        throw new DocumentProcessingException("UNSUPPORTED_FILE_TYPE", "Only PDF, PNG, JPG, and JPEG files are supported");
    }
    private boolean starts(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if ((bytes[i] & 0xff) != signature[i]) return false;
        return true;
    }
}
