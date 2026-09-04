package com.invoiceparse.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LineItemResponse(
        String productName, String description, String hsnSac, String batchNumber,
        LocalDate expiryDate, BigDecimal quantity, String unit, BigDecimal unitRate,
        BigDecimal discount, BigDecimal gstPercentage, BigDecimal taxableAmount,
        BigDecimal lineTotal, double confidence
) { }
