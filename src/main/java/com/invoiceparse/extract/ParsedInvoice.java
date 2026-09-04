package com.invoiceparse.extract;

import com.invoiceparse.api.LineItemResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

public class ParsedInvoice {
    public String invoiceNumber;
    public LocalDate invoiceDate;
    public String supplierName;
    public String supplierGstin;
    public String customerName;
    public String customerGstin;
    public String address;
    public BigDecimal subtotal;
    public BigDecimal discount;
    public BigDecimal cgst;
    public BigDecimal sgst;
    public BigDecimal igst;
    public BigDecimal taxableAmount;
    public BigDecimal roundOff;
    public BigDecimal grandTotal;
    public String currency = "INR";
    public List<LineItemResponse> lineItems = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();
    public Map<String, Double> fieldConfidences = new LinkedHashMap<>();
    public Set<String> expectedFields = new LinkedHashSet<>();
    public double extractionConfidence;
}
