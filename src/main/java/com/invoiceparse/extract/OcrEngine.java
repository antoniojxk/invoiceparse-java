package com.invoiceparse.extract;

import com.invoiceparse.model.TextToken;
import java.awt.image.BufferedImage;
import java.util.List;

public interface OcrEngine {
    OcrResult recognize(BufferedImage image, int page);
    record OcrResult(String text, List<TextToken> tokens) { }
}
