package com.invoiceparse.validation;

import com.invoiceparse.api.LineItemResponse;
import com.invoiceparse.config.InvoiceParseProperties;
import com.invoiceparse.extract.ParsedInvoice;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class InvoiceValidatorTest {
    private final InvoiceValidator validator = new InvoiceValidator(new GstinValidator(),
            new InvoiceParseProperties(40, 250, "tesseract", "eng", 60, .7, .1,
                    25, 40_000_000, 12_000, 0,
                    new InvoiceParseProperties.DemoAccess(false, 600, 5, 30, 1)));
    @Test void validatesLineAndInvoiceArithmetic() {
        var invoice = new ParsedInvoice();
        invoice.supplierGstin = "27ABCDE1234F1Z5";
        invoice.taxableAmount = new BigDecimal("1000"); invoice.cgst = new BigDecimal("90");
        invoice.sgst = new BigDecimal("90"); invoice.grandTotal = new BigDecimal("1180");
        invoice.lineItems = List.of(new LineItemResponse("Item", "Item", null, null, null,
                new BigDecimal("2"), "pcs", new BigDecimal("500"), null, null,
                new BigDecimal("1000"), new BigDecimal("1000"), .8));
        assertThat(validator.validate(invoice)).allMatch(v -> v.valid());
    }
    @Test void flagsInconsistentTotal() {
        var invoice = new ParsedInvoice(); invoice.subtotal = new BigDecimal("100"); invoice.grandTotal = new BigDecimal("130");
        assertThat(validator.validate(invoice)).anyMatch(v -> v.code().equals("INVOICE_TOTAL") && !v.valid());
    }
    @Test void flagsOcrScaleErrorOutsideTenPaiseTolerance() {
        var invoice = new ParsedInvoice();
        invoice.subtotal = new BigDecimal("1950.00");
        invoice.igst = new BigDecimal("351.60");
        invoice.grandTotal = new BigDecimal("2301.00");
        assertThat(validator.validate(invoice)).anyMatch(v -> v.code().equals("INVOICE_TOTAL") && !v.valid());
    }
}
