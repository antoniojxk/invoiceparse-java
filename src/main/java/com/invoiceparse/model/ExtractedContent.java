package com.invoiceparse.model;

import java.util.List;

public record ExtractedContent(String text, List<TextToken> tokens, SourceType sourceType, int pageCount) {
    public ExtractedContent {
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
    }
}
