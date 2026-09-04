package com.invoiceparse.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LineItemResponse(
        String productName, String description, String hsnSac, String batchNumber,
        LocalDate expiryDate, BigDecimal quantity, String unit, BigDecimal unitRate,
        BigDecimal discount, BigDecimal gstPercentage, BigDecimal taxableAmount,
        BigDecimal lineTotal, double confidence,
        Integer serialNumber, String quantityText, BigDecimal freeQuantity, String pack,
        String expiryText, BigDecimal mrp, BigDecimal discountPercentage,
        BigDecimal sgstPercentage, BigDecimal sgstAmount,
        BigDecimal cgstPercentage, BigDecimal cgstAmount,
        BigDecimal igstPercentage, BigDecimal igstAmount
) {
    public LineItemResponse(
            String productName, String description, String hsnSac, String batchNumber,
            LocalDate expiryDate, BigDecimal quantity, String unit, BigDecimal unitRate,
            BigDecimal discount, BigDecimal gstPercentage, BigDecimal taxableAmount,
            BigDecimal lineTotal, double confidence
    ) {
        this(productName, description, hsnSac, batchNumber, expiryDate, quantity, unit,
                unitRate, discount, gstPercentage, taxableAmount, lineTotal, confidence,
                null, quantity == null ? null : quantity.toPlainString(), null, null,
                expiryDate == null ? null : expiryDate.toString(), null, null,
                null, null, null, null, null, null);
    }
}
