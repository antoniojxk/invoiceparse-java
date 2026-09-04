package com.invoiceparse.extract;

import com.invoiceparse.model.ExtractedContent;
import com.invoiceparse.model.DocumentType;
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

    @Test void extractsPurchaseBillPartiesBatchExpiryMrpAndFreeQuantity() {
        var invoice = extractor.extract(content("""
                PURCHASE BILL
                Pharma Disttribution Pvt Ltd Party Name : ADONIS PHYTOCEUTICALS PVT. LTD.
                State Code:07- Delhi
                GSTIN:07AABCU9603R1ZP Invoice No.:OP000004 Invoice Date:04-09-2026
                S.No. Qty. Description Batch Exp HSN MRP Rate Dis. % Amount
                1 10 ACNE AID BAR 100GM ACN-100G 12-2035 3004 217.60 139.89 0.00 1,398.90
                2 37+7 ADVAN INSTA 20GM AD-34 12-2035 3004 325.00 231.36 0.00 8,560.32
                Sub Total 9,959.22
                Bill Dis 0.00
                Round Off 0.00
                Grand Total 9,959.22
                """));

        assertThat(invoice.documentType).isEqualTo(DocumentType.PURCHASE_BILL);
        assertThat(invoice.invoiceNumber).isEqualTo("OP000004");
        assertThat(invoice.supplierName).isEqualTo("ADONIS PHYTOCEUTICALS PVT. LTD");
        assertThat(invoice.customerName).isEqualTo("Pharma Disttribution Pvt Ltd");
        assertThat(invoice.customerGstin).isEqualTo("07AABCU9603R1ZP");
        assertThat(invoice.lineItems).hasSize(2);
        assertThat(invoice.lineItems.getFirst().batchNumber()).isEqualTo("ACN-100G");
        assertThat(invoice.lineItems.getFirst().expiryText()).isEqualTo("12-2035");
        assertThat(invoice.lineItems.getFirst().mrp()).isEqualByComparingTo("217.60");
        assertThat(invoice.lineItems.get(1).quantity()).isEqualByComparingTo("37");
        assertThat(invoice.lineItems.get(1).freeQuantity()).isEqualByComparingTo("7");
    }

    @Test void extractsLandscapeSalesOrderPartiesAndPerLineIgst() {
        var invoice = extractor.extract(content("""
                Pharma Disttribution Pvt Ltd Sale Order 2MG PHARMACY (P.P.1)
                Invoice No:OS000001 Order No.
                GSTIN:09AAACH7409R1ZZ
                Invoice Date:04-09-2026
                GSTIN:07AABCU9603R1ZP Due Date:
                State Code:07- Delhi
                S.No. Qty. Pack Product Batch Exp HSN Rate Dis. % IGST% IGST Amount
                1 1 1*10 ACNE AID FACEWASH 3014 360.00 0.00 5.00 18.00 360.00
                Class SUB TOTAL SCHEME DISCOUNT SGST IGST TOTAL GST T. Items:- 1.00 Sub Total 360.00
                IGST 18.00
                Round Off 0.00
                Grand Total 378.00
                """));

        assertThat(invoice.documentType).isEqualTo(DocumentType.SALES_ORDER);
        assertThat(invoice.supplierName).isEqualTo("Pharma Disttribution Pvt Ltd");
        assertThat(invoice.supplierGstin).isEqualTo("07AABCU9603R1ZP");
        assertThat(invoice.customerName).isEqualTo("2MG PHARMACY (P.P.1)");
        assertThat(invoice.customerGstin).isEqualTo("09AAACH7409R1ZZ");
        assertThat(invoice.lineItems).singleElement().satisfies(item -> {
            assertThat(item.pack()).isEqualTo("1*10");
            assertThat(item.igstPercentage()).isEqualByComparingTo("5.00");
            assertThat(item.igstAmount()).isEqualByComparingTo("18.00");
        });
    }

    @Test void recoversADataRowInterruptedByWatermarkText() {
        var invoice = extractor.extract(content("""
                PURCHASE ORDER
                Pharma Disttribution Pvt Ltd
                ADONIS PHYTOCEUTICALS PVT. LTD. Invoice No. : OP000004
                Invoice Date : 04-09-2026
                S.No. Qty. Product Batch Exp HSN Rate Dis. % SGST CGST Amount
                5 37+7 ADVAN INSTA 20GM 3004MA231.36
                PHAR
                DISTTRIBUTIO
                0.00 N0.00 0.00 8,560.32
                PVT LTD
                Class TOTAL SCHEME DISCOUNT SGST CGST TOTAL GST
                Sub Total 8,560.32
                Grand Total 8,560.32
                """));

        assertThat(invoice.lineItems).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("ADVAN INSTA 20GM");
            assertThat(item.hsnSac()).isEqualTo("3004");
            assertThat(item.unitRate()).isEqualByComparingTo("231.36");
            assertThat(item.freeQuantity()).isEqualByComparingTo("7");
            assertThat(item.lineTotal()).isEqualByComparingTo("8560.32");
        });
    }

    private ExtractedContent content(String text) { return new ExtractedContent(text, List.of(), SourceType.DIGITAL_PDF, 1); }
}
