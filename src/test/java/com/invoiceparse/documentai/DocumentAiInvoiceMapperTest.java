package com.invoiceparse.documentai;

import com.google.cloud.documentai.v1.Document;
import com.invoiceparse.extract.DocumentInputInspector;
import com.invoiceparse.model.DocumentType;
import com.invoiceparse.model.SourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentAiInvoiceMapperTest {
    private final DocumentAiInvoiceMapper mapper = new DocumentAiInvoiceMapper();

    @Test
    void mapsCustomExtractorEntitiesAndNestedLineItems() {
        var lineItem = entity("line_items", "", .96f,
                entity("line_items/product_name", "ACNE AID BAR 100GM", .95f),
                entity("line_items/description", "Acne cleansing bar", .91f),
                entity("line_items/hsn_sac", "3004", .99f),
                entity("line_items/batch_number", "ACN-100G", .97f),
                entity("line_items/expiry_date", "12-2035", .93f),
                entity("line_items/quantity", "37+7", .94f),
                entity("line_items/unit", "box", .90f),
                entity("line_items/unit_rate", "231.36", .98f),
                entity("line_items/discount_item", "12.50", .98f),
                entity("line_items/gst_percentage", "18", .98f),
                entity("line_items/taxable_amount_item", "8560.32", .97f),
                entity("line_items/line_total", "8560.32", .99f));
        var document = Document.newBuilder()
                .setText("synthetic response")
                .addEntities(entity("document_type", "purchase bill", .98f))
                .addEntities(entity("invoice_number", "OP000004", .99f))
                .addEntities(entity("invoice_date", "04-09-2026", .99f))
                .addEntities(entity("supplier_name", "ADONIS PHYTOCEUTICALS PVT. LTD.", .96f))
                .addEntities(entity("supplier_gstin", "07 AABCU9603R1ZP", .95f))
                .addEntities(entity("customer_name", "Pharma Distribution Pvt Ltd", .96f))
                .addEntities(entity("customer_gstin", "07AABCU9603R1ZP", .95f))
                .addEntities(entity("supplier_address", "Lajpat Nagar, New Delhi", .90f))
                .addEntities(entity("customer_address", "Plot No 7, New Delhi", .91f))
                .addEntities(entity("subtotal", "32,396.87", .99f))
                .addEntities(entity("discount", "3,599.65", .98f))
                .addEntities(entity("taxable_amount", "28,797.22", .96f))
                .addEntities(entity("round_off", "0.13", .97f))
                .addEntities(entity("grand_total", "32,397.00", .99f))
                .addEntities(entity("currency_code", "INR", .99f))
                .addEntities(lineItem)
                .build();

        var result = mapper.map(document,
                new DocumentInputInspector.Metadata("application/pdf", SourceType.DIGITAL_PDF, 1));
        var invoice = result.invoice();

        assertThat(invoice.documentType).isEqualTo(DocumentType.PURCHASE_BILL);
        assertThat(invoice.invoiceNumber).isEqualTo("OP000004");
        assertThat(invoice.invoiceDate).hasToString("2026-09-04");
        assertThat(invoice.supplierGstin).isEqualTo("07AABCU9603R1ZP");
        assertThat(invoice.supplierAddress).isEqualTo("Lajpat Nagar, New Delhi");
        assertThat(invoice.customerAddress).isEqualTo("Plot No 7, New Delhi");
        assertThat(invoice.grandTotal).isEqualByComparingTo("32397.00");
        assertThat(invoice.currency).isEqualTo("INR");
        assertThat(invoice.lineItems).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("ACNE AID BAR 100GM");
            assertThat(item.batchNumber()).isEqualTo("ACN-100G");
            assertThat(item.expiryText()).isEqualTo("12-2035");
            assertThat(item.expiryDate()).isNull();
            assertThat(item.quantity()).isEqualByComparingTo("37");
            assertThat(item.freeQuantity()).isEqualByComparingTo("7");
            assertThat(item.gstPercentage()).isEqualByComparingTo("18");
            assertThat(item.discount()).isEqualByComparingTo("12.50");
            assertThat(item.taxableAmount()).isEqualByComparingTo("8560.32");
            assertThat(item.lineTotal()).isEqualByComparingTo("8560.32");
        });
        assertThat(result.content().sourceType()).isEqualTo(SourceType.DIGITAL_PDF);
        assertThat(invoice.warnings).isEmpty();
    }

    @Test
    void flagsMissingEssentialFieldsAndInvalidNumericValuesForReview() {
        var document = Document.newBuilder()
                .addEntities(entity("invoice_number", "INV-7", .92f))
                .addEntities(entity("grand_total", "not a number", .80f))
                .build();

        var invoice = mapper.map(document,
                new DocumentInputInspector.Metadata("image/png", SourceType.IMAGE, 1)).invoice();

        assertThat(invoice.grandTotal).isNull();
        assertThat(invoice.fieldConfidences).containsEntry("invoiceDate", 0.0)
                .containsEntry("supplierName", 0.0).containsEntry("customerName", 0.0)
                .containsEntry("grandTotal", 0.0);
        assertThat(invoice.warnings).anyMatch(value -> value.contains("invalid grand total"))
                .anyMatch(value -> value.contains("invoice date was not returned"));
    }

    private static Document.Entity entity(String type, String value, float confidence,
                                           Document.Entity... properties) {
        return Document.Entity.newBuilder()
                .setType(type)
                .setMentionText(value)
                .setConfidence(confidence)
                .addAllProperties(java.util.List.of(properties))
                .build();
    }
}
