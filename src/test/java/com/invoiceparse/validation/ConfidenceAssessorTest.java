package com.invoiceparse.validation;

import com.invoiceparse.api.ValidationResultResponse;
import com.invoiceparse.config.InvoiceParseProperties;
import com.invoiceparse.extract.ParsedInvoice;
import com.invoiceparse.extract.InvoiceFieldExtractor;
import com.invoiceparse.extract.LineItemExtractor;
import com.invoiceparse.model.ExtractedContent;
import com.invoiceparse.model.SourceType;
import com.invoiceparse.model.TextToken;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceAssessorTest {
    private final ConfidenceAssessor assessor = new ConfidenceAssessor(
            properties());

    @Test void requiresReviewDespiteHighOcrConfidenceWhenExpectedGstinsAreMissing() {
        var invoice = new ParsedInvoice();
        invoice.extractionConfidence = .95;
        invoice.expectedFields.addAll(List.of("supplierGstin", "customerGstin"));
        invoice.fieldConfidences.put("supplierGstin", 0.0);
        invoice.fieldConfidences.put("customerGstin", 0.0);
        var content = new ExtractedContent("GST INVOICE", List.of(
                new TextToken("GST", 1, 0, 0, 10, 10, .96)), SourceType.SCANNED_PDF, 1);
        var invalidTotal = new ValidationResultResponse("INVOICE_TOTAL", "grandTotal", false,
                "Grand total does not match the extracted components");

        var result = assessor.assess(invoice, content, List.of(invalidTotal));

        assertThat(result.manualReviewRequired()).isTrue();
        assertThat(result.overallConfidence()).isLessThan(.70);
    }

    @Test void reviewedScannedPdfFailureCannotBeConfidentlyAccepted() {
        var properties = properties();
        var content = new ExtractedContent("""
                NORTHWIND WHOLESALE
                GST INVOICE
                Supplier GSTIN: 1SABCDE1234F1Z7
                Customer: Sample Office Supplies
                Customer GSTIM: 2SPQRSX5678K1Z2
                Bill No: PI-7781
                Date: 18/08/2026
                Product Description Quantity Rate Amount
                Paper Ream A4 5 240.00 1200.00
                Marker Pack 2 150.00 300.00
                Desk Organizer 1 450.00 450.00
                Subtotal: 1950.00
                IGST: 351.60
                Grand Total: INR 2301.00
                """, List.of(new TextToken("invoice", 1, 0, 0, 10, 10, .96)), SourceType.SCANNED_PDF, 1);
        var invoice = new InvoiceFieldExtractor(new LineItemExtractor()).extract(content);
        var validation = new InvoiceValidator(new GstinValidator(), properties).validate(invoice);

        var result = new ConfidenceAssessor(properties).assess(invoice, content, validation);

        assertThat(validation).anyMatch(v -> v.code().equals("INVOICE_TOTAL") && !v.valid());
        assertThat(result.manualReviewRequired()).isTrue();
        assertThat(result.overallConfidence()).isLessThan(.70);
    }

    private static InvoiceParseProperties properties() {
        return new InvoiceParseProperties(40, 250, "tesseract", "eng", 60, .7, .1,
                25, 40_000_000, 12_000, 0,
                new InvoiceParseProperties.DemoAccess(false, 600, 5, 30, 1));
    }
}
