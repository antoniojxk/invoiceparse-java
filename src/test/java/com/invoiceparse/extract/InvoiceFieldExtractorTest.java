package com.invoiceparse.extract;

import com.invoiceparse.model.ExtractedContent;
import com.invoiceparse.model.SourceType;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class InvoiceFieldExtractorTest {
    private final InvoiceFieldExtractor extractor = new InvoiceFieldExtractor(new LineItemExtractor());

    @Test void extractsHeaderFieldsAndPipeDelimitedLayout() {
        var invoice = extractor.extract(content("""
                TAX INVOICE
                Supplier: Example Components Pvt Ltd
                Supplier GSTIN: 27ABCDE1234F1Z5
                Customer: Demo Retail LLP
                Customer GSTIN: 29PQRSX5678K1Z2
                Invoice No: SI-2026-104
                Invoice Date: 07-Aug-2026
                Description | HSN/SAC | Qty | Unit | Rate | GST % | Taxable Amount | Total
                Copper Cable | 8544 | 2 | roll | 500.00 | 18 | 1000.00 | 1000.00
                USB Adapter | 8504 | 3 | pcs | 200.00 | 18 | 600.00 | 600.00
                Taxable Amount: 1600.00
                CGST: 144.00
                SGST: 144.00
                Grand Total: 1888.00
                """));
        assertThat(invoice.invoiceNumber).isEqualTo("SI-2026-104");
        assertThat(invoice.invoiceDate).isEqualTo(LocalDate.of(2026, 8, 7));
        assertThat(invoice.supplierGstin).isEqualTo("27ABCDE1234F1Z5");
        assertThat(invoice.customerName).isEqualTo("Demo Retail LLP");
        assertThat(invoice.grandTotal).isEqualByComparingTo("1888.00");
        assertThat(invoice.lineItems).hasSize(2);
        assertThat(invoice.lineItems.getFirst().hsnSac()).isEqualTo("8544");
    }

    @Test void extractsWhitespaceLayoutWithNumericTail() {
        var invoice = extractor.extract(content("""
                NORTHWIND WHOLESALE
                Bill No: PI-7781
                Date: 18/08/2026
                Product Description    Quantity    Rate    Amount
                Paper Ream A4          5           240.00  1200.00
                Marker Pack            2           150.00  300.00
                Invoice Total: 1500.00
                """));
        assertThat(invoice.invoiceNumber).isEqualTo("PI-7781");
        assertThat(invoice.supplierName).isEqualTo("NORTHWIND WHOLESALE");
        assertThat(invoice.lineItems).hasSize(2);
        assertThat(invoice.lineItems.get(1).quantity()).isEqualByComparingTo(BigDecimal.valueOf(2));
        assertThat(invoice.lineItems.get(1).lineTotal()).isEqualByComparingTo("300.00");
    }

    @Test void doesNotTreatSupplierGstinAsSupplierName() {
        var invoice = extractor.extract(content("""
                NORTHWIND WHOLESALE
                GST INVOICE
                Supplier GSTIN: 19ABCDE1234F1Z7
                Customer: Sample Office Supplies
                Bill No: PI-7781
                Grand Total: 10.00
                """));
        assertThat(invoice.supplierName).isEqualTo("NORTHWIND WHOLESALE");
    }

    @Test void marksExpectedButUnreadableGstinsAsZeroConfidence() {
        var invoice = extractor.extract(new ExtractedContent("""
                GST INVOICE
                Supplier GSTIN: 1SABCDE1234F1Z7
                Customer: Sample Office Supplies
                Customer GSTIM: 2SPQRSX5678K1Z2
                Bill No: PI-7781
                Subtotal: 1950.00
                IGST: 351.60
                Grand Total: 2301.00
                """, List.of(), SourceType.SCANNED_PDF, 1));
        assertThat(invoice.supplierGstin).isNull();
        assertThat(invoice.customerGstin).isNull();
        assertThat(invoice.fieldConfidences).containsEntry("supplierGstin", 0.0).containsEntry("customerGstin", 0.0);
        assertThat(invoice.warnings).contains("Supplier GSTIN was expected but could not be extracted",
                "Customer GSTIN was expected but could not be extracted");
    }

    @Test void rejectsAmbiguousOrInconsistentNumericTailRows() {
        var inconsistent = extractor.extract(content("""
                Invoice No: BAD-1
                Product Description Quantity Rate Amount
                Widget 2 100.00 260.00
                Grand Total: 260.00
                """));
        var richUndelimited = extractor.extract(content("""
                Invoice No: BAD-2
                Description HSN Qty Rate GST Amount
                Widget 1234 2 100.00 18 200.00
                Grand Total: 200.00
                """));
        assertThat(inconsistent.lineItems).isEmpty();
        assertThat(richUndelimited.lineItems).isEmpty();
    }

    private ExtractedContent content(String text) { return new ExtractedContent(text, List.of(), SourceType.DIGITAL_PDF, 1); }
}
