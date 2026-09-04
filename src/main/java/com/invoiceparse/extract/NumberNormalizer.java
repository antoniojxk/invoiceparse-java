package com.invoiceparse.extract;

import java.math.BigDecimal;
import java.util.Optional;

public final class NumberNormalizer {
    private NumberNormalizer() { }
    public static Optional<BigDecimal> parse(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String value = input.replaceAll("(?i)(INR|Rs\\.?|₹|USD|EUR|GBP)", "")
                .replace(",", "").replaceAll("\\s", "").trim();
        // Never silently turn product codes or OCR substitutions such as A4/1O0 into numbers.
        if (value.matches(".*[A-Za-z].*")) return Optional.empty();
        if (value.startsWith("(") && value.endsWith(")")) value = "-" + value.substring(1, value.length() - 1);
        value = value.replaceAll("[^0-9.+-]", "");
        if (!value.matches("[+-]?\\d+(?:\\.\\d+)?")) return Optional.empty();
        try { return Optional.of(new BigDecimal(value)); } catch (NumberFormatException e) { return Optional.empty(); }
    }
}
