package com.invoiceparse.model;

public record TextToken(String text, int page, float x, float y, float width, float height, double confidence) { }
