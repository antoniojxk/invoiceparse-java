package com.invoiceparse.validation;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class GstinValidator {
    private static final Pattern FORMAT = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    public boolean isValid(String value) { return value != null && FORMAT.matcher(value.toUpperCase()).matches(); }
}
