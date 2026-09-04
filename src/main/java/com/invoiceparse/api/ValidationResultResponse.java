package com.invoiceparse.api;

public record ValidationResultResponse(String code, String field, boolean valid, String message) { }
