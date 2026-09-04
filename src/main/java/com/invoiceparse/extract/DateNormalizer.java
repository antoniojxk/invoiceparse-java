package com.invoiceparse.extract;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class DateNormalizer {
    private static final List<DateTimeFormatter> FORMATS = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"), DateTimeFormatter.ofPattern("uuuu-M-d"),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH), DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/uu"), DateTimeFormatter.ofPattern("d-M-uu"));
    private DateNormalizer() { }
    public static Optional<LocalDate> parse(String input) {
        if (input == null) return Optional.empty();
        String value = input.trim().replaceAll("\\s+", " ");
        for (var format : FORMATS) try { return Optional.of(LocalDate.parse(value, format)); } catch (DateTimeParseException ignored) { }
        return Optional.empty();
    }
}
