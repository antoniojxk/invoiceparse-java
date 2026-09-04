package com.invoiceparse.extract;

import java.math.BigDecimal;
import java.util.Optional;

public final class NumberNormalizer {
    private NumberNormalizer() { }
    public static Optional<BigDecimal> parse(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String value = input.replaceAll("(?i)(INR|Rs\\.?|₹|USD|EUR|GBP)", "")
                .replace(",", "").replaceAll("\\s", "").trim();
        if (value.startsWith("(") && value.endsWith(")")) value = "-" + value.substring(1, value.length() - 1);
        value = value.replaceAll("[^0-9.+-]", "");
        if (!value.matches("[+-]?\\d+(?:\\.\\d+)?")) return Optional.empty();
        try { return Optional.of(new BigDecimal(value)); } catch (NumberFormatException e) { return Optional.empty(); }
    }
}
