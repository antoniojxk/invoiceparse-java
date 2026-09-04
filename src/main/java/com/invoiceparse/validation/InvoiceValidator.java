package com.invoiceparse.validation;

import com.invoiceparse.api.ValidationResultResponse;
import com.invoiceparse.config.InvoiceParseProperties;
import com.invoiceparse.extract.ParsedInvoice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class InvoiceValidator {
    private final GstinValidator gstin;
    private final BigDecimal tolerance;
    public InvoiceValidator(GstinValidator gstin, InvoiceParseProperties properties) {
        this.gstin = gstin; this.tolerance = BigDecimal.valueOf(properties.totalTolerance());
    }

    public List<ValidationResultResponse> validate(ParsedInvoice invoice) {
        var results = new ArrayList<ValidationResultResponse>();
        validateGstin(results, "supplierGstin", invoice.supplierGstin);
        validateGstin(results, "customerGstin", invoice.customerGstin);
        for (int i = 0; i < invoice.lineItems.size(); i++) {
            var item = invoice.lineItems.get(i);
            nonNegative(results, "lineItems[" + i + "].quantity", item.quantity());
            nonNegative(results, "lineItems[" + i + "].lineTotal", item.lineTotal());
            if (item.quantity() != null && item.unitRate() != null && item.lineTotal() != null) {
                BigDecimal expected = item.quantity().multiply(item.unitRate());
                if (item.discount() != null) expected = expected.subtract(item.discount());
                boolean valid = expected.subtract(item.lineTotal()).abs().compareTo(tolerance) <= 0;
                results.add(new ValidationResultResponse("LINE_TOTAL", "lineItems[" + i + "].lineTotal", valid,
                        valid ? "Line total is consistent" : "Line total differs from quantity × rate minus discount"));
            }
        }
        validateTotal(results, invoice);
        return results;
    }

    private void validateGstin(List<ValidationResultResponse> results, String field, String value) {
        if (value == null) return;
        boolean valid = gstin.isValid(value);
        results.add(new ValidationResultResponse("GSTIN_FORMAT", field, valid,
                valid ? "GSTIN format is valid" : "GSTIN format is invalid"));
    }

    private void nonNegative(List<ValidationResultResponse> results, String field, BigDecimal value) {
        if (value != null && value.signum() < 0) results.add(new ValidationResultResponse("NON_NEGATIVE", field, false, "Value must not be negative"));
    }

    private void validateTotal(List<ValidationResultResponse> results, ParsedInvoice invoice) {
        if (invoice.grandTotal == null) return;
        BigDecimal base = invoice.taxableAmount != null ? invoice.taxableAmount : invoice.subtotal;
        if (base == null && !invoice.lineItems.isEmpty()) base = invoice.lineItems.stream()
                .map(i -> i.lineTotal() == null ? BigDecimal.ZERO : i.lineTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (base == null) return;
        BigDecimal expected = base;
        if (invoice.taxableAmount == null && invoice.discount != null) expected = expected.subtract(invoice.discount);
        if (invoice.cgst != null) expected = expected.add(invoice.cgst);
        if (invoice.sgst != null) expected = expected.add(invoice.sgst);
        if (invoice.igst != null) expected = expected.add(invoice.igst);
        if (invoice.roundOff != null) expected = expected.add(invoice.roundOff);
        boolean valid = expected.subtract(invoice.grandTotal).abs().compareTo(tolerance) <= 0;
        results.add(new ValidationResultResponse("INVOICE_TOTAL", "grandTotal", valid,
                valid ? "Invoice total is consistent" : "Grand total does not match the extracted components"));
    }
}
