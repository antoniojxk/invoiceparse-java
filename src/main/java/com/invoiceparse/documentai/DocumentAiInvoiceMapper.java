package com.invoiceparse.documentai;

import com.google.cloud.documentai.v1.Document;
import com.invoiceparse.api.LineItemResponse;
import com.invoiceparse.extract.DateNormalizer;
import com.invoiceparse.extract.DocumentInputInspector;
import com.invoiceparse.extract.InvoiceExtractionProvider;
import com.invoiceparse.extract.NumberNormalizer;
import com.invoiceparse.extract.ParsedInvoice;
import com.invoiceparse.model.DocumentType;
import com.invoiceparse.model.ExtractedContent;
import com.invoiceparse.model.TextToken;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class DocumentAiInvoiceMapper {
    private static final List<String> EXPECTED_FIELDS = List.of(
            "invoiceNumber", "invoiceDate", "supplierName", "customerName", "grandTotal");

    public InvoiceExtractionProvider.ExtractionResult map(Document document,
                                                           DocumentInputInspector.Metadata metadata) {
        var invoice = new ParsedInvoice();
        invoice.currency = null;
        invoice.documentType = DocumentType.INVOICE;

        Map<String, Document.Entity> fields = highestConfidenceFields(document.getEntitiesList());
        applyText(invoice, fields, "invoiceNumber", value(fields, "invoice_number", "invoice_id"),
                value -> invoice.invoiceNumber = value);
        applyDate(invoice, fields, "invoiceDate", entity(fields, "invoice_date"),
                value -> invoice.invoiceDate = value);
        applyText(invoice, fields, "supplierName", value(fields, "supplier_name", "vendor_name", "seller_name"),
                value -> invoice.supplierName = value);
        applyGstin(invoice, fields, "supplierGstin",
                entity(fields, "supplier_gstin", "supplier_tax_id", "vendor_gstin"),
                value -> invoice.supplierGstin = value);
        applyText(invoice, fields, "customerName",
                value(fields, "customer_name", "receiver_name", "buyer_name"),
                value -> invoice.customerName = value);
        applyGstin(invoice, fields, "customerGstin",
                entity(fields, "customer_gstin", "receiver_tax_id", "buyer_gstin"),
                value -> invoice.customerGstin = value);
        applyText(invoice, fields, "supplierAddress",
                value(fields, "supplier_address", "vendor_address", "seller_address"),
                value -> invoice.supplierAddress = value);
        applyText(invoice, fields, "customerAddress",
                value(fields, "customer_address", "receiver_address", "billing_address", "address"),
                value -> invoice.customerAddress = value);
        applyAmount(invoice, fields, "subtotal", entity(fields, "subtotal", "sub_total"),
                value -> invoice.subtotal = value);
        applyAmount(invoice, fields, "discount", entity(fields, "discount", "total_discount"),
                value -> invoice.discount = value);
        applyAmount(invoice, fields, "cgst", entity(fields, "cgst", "cgst_amount"), value -> invoice.cgst = value);
        applyAmount(invoice, fields, "sgst", entity(fields, "sgst", "sgst_amount"), value -> invoice.sgst = value);
        applyAmount(invoice, fields, "igst", entity(fields, "igst", "igst_amount"), value -> invoice.igst = value);
        applyAmount(invoice, fields, "taxableAmount",
                entity(fields, "taxable_amount", "net_amount"), value -> invoice.taxableAmount = value);
        applyAmount(invoice, fields, "roundOff", entity(fields, "round_off", "rounding_off"),
                value -> invoice.roundOff = value);
        applyAmount(invoice, fields, "grandTotal",
                entity(fields, "grand_total", "total_amount", "invoice_total"), value -> invoice.grandTotal = value);
        applyText(invoice, fields, "currency", value(fields, "currency", "currency_code"),
                value -> invoice.currency = value.toUpperCase(Locale.ROOT));

        Document.Entity documentType = entity(fields, "document_type");
        if (documentType != null) invoice.documentType = documentType(textValue(documentType));
        invoice.lineItems = lineItems(document.getEntitiesList(), invoice);
        if (invoice.currency == null && (invoice.supplierGstin != null || invoice.customerGstin != null)) {
            invoice.currency = "INR";
        }

        List<Double> confidences = flatten(document.getEntitiesList()).stream()
                .map(entity -> (double) entity.getConfidence()).filter(value -> value > 0).toList();
        invoice.extractionConfidence = confidences.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        for (String field : EXPECTED_FIELDS) {
            invoice.expectedFields.add(field);
            if (!invoice.fieldConfidences.containsKey(field)) {
                invoice.fieldConfidences.put(field, 0.0);
                invoice.warnings.add(displayName(field) + " was not returned by Google Document AI");
            }
        }
        if (invoice.lineItems.isEmpty()) invoice.warnings.add("No line items were returned by Google Document AI");

        List<TextToken> confidenceTokens = flatten(document.getEntitiesList()).stream()
                .filter(entity -> entity.getConfidence() > 0)
                .map(this::confidenceToken)
                .toList();
        int pageCount = document.getPagesCount() == 0 ? metadata.pageCount() : document.getPagesCount();
        var content = new ExtractedContent(document.getText(), confidenceTokens, metadata.sourceType(), pageCount);
        return new InvoiceExtractionProvider.ExtractionResult(invoice, content);
    }

    private Map<String, Document.Entity> highestConfidenceFields(List<Document.Entity> entities) {
        Map<String, Document.Entity> result = new LinkedHashMap<>();
        for (Document.Entity entity : entities) {
            String type = normalizeType(entity.getType());
            if (type.equals("line_item") || type.equals("line_items")) continue;
            result.merge(type, entity, (left, right) ->
                    left.getConfidence() >= right.getConfidence() ? left : right);
        }
        return result;
    }

    private List<LineItemResponse> lineItems(List<Document.Entity> entities, ParsedInvoice invoice) {
        var result = new ArrayList<LineItemResponse>();
        int index = 0;
        for (Document.Entity entity : entities) {
            String type = normalizeType(entity.getType());
            if (!type.equals("line_item") && !type.equals("line_items")) continue;
            Map<String, Document.Entity> fields = highestConfidenceFields(entity.getPropertiesList());
            String productName = text(fields, "product_name", "item_name");
            String description = text(fields, "description", "product_description");
            if (productName == null) productName = description;
            if (description == null) description = productName;
            String hsnSac = text(fields, "hsn_sac", "hsn", "sac");
            String batchNumber = text(fields, "batch_number", "batch");
            String expiryText = text(fields, "expiry_date", "expiration_date", "expiry");
            LocalDate expiryDate = DateNormalizer.parse(normalized(fields, "expiry_date", "expiration_date", "expiry")).orElse(null);
            String quantityText = text(fields, "quantity", "quantity_text", "qty");
            Quantity quantity = quantity(quantityText);
            BigDecimal freeQuantity = amount(fields, "free_quantity", "free_qty");
            if (freeQuantity == null) freeQuantity = quantity.free();
            BigDecimal unitRate = amount(fields, "unit_rate", "unit_price", "rate");
            BigDecimal discount = amount(fields, "discount_item", "discount_line", "discount", "discount_amount");
            BigDecimal gstPercentage = amount(fields, "gst_percentage", "gst_percent", "gst_rate");
            BigDecimal taxableAmount = amount(fields, "taxable_amount_item", "taxable_amount_line", "taxable_amount");
            BigDecimal lineTotal = amount(fields, "line_total", "amount", "total");
            double confidence = entity.getPropertiesList().stream().mapToDouble(Document.Entity::getConfidence)
                    .filter(value -> value > 0).average().orElse(entity.getConfidence());
            result.add(new LineItemResponse(productName, description, hsnSac, batchNumber, expiryDate,
                    quantity.paid(), text(fields, "unit"), unitRate, discount, gstPercentage, taxableAmount,
                    lineTotal, confidence, integer(fields, "serial_number", "serial_no"), quantityText,
                    freeQuantity, text(fields, "pack"), expiryText, amount(fields, "mrp"),
                    amount(fields, "discount_percentage", "discount_percent"),
                    amount(fields, "sgst_percentage"), amount(fields, "sgst_amount"),
                    amount(fields, "cgst_percentage"), amount(fields, "cgst_amount"),
                    amount(fields, "igst_percentage"), amount(fields, "igst_amount")));
            for (var field : fields.entrySet()) {
                invoice.fieldConfidences.put("lineItems[" + index + "]." + camelCase(field.getKey()),
                        (double) field.getValue().getConfidence());
            }
            index++;
        }
        return List.copyOf(result);
    }

    private void applyText(ParsedInvoice invoice, Map<String, Document.Entity> fields, String outputField,
                           EntityValue entityValue, Consumer<String> setter) {
        if (entityValue == null || entityValue.value().isBlank()) return;
        setter.accept(entityValue.value().trim());
        invoice.fieldConfidences.put(outputField, (double) entityValue.entity().getConfidence());
    }

    private void applyGstin(ParsedInvoice invoice, Map<String, Document.Entity> fields, String outputField,
                            Document.Entity entity, Consumer<String> setter) {
        if (entity == null) return;
        String value = textValue(entity).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (value.isBlank()) return;
        setter.accept(value);
        invoice.fieldConfidences.put(outputField, (double) entity.getConfidence());
    }

    private void applyDate(ParsedInvoice invoice, Map<String, Document.Entity> fields, String outputField,
                           Document.Entity entity, Consumer<LocalDate> setter) {
        if (entity == null) return;
        String value = normalizedValue(entity);
        var parsed = DateNormalizer.parse(value);
        if (parsed.isPresent()) {
            setter.accept(parsed.get());
            invoice.fieldConfidences.put(outputField, (double) entity.getConfidence());
        } else {
            invoice.warnings.add("Google Document AI returned an invalid " + displayName(outputField) + ": " + value);
        }
    }

    private void applyAmount(ParsedInvoice invoice, Map<String, Document.Entity> fields, String outputField,
                             Document.Entity entity, Consumer<BigDecimal> setter) {
        if (entity == null) return;
        String value = normalizedValue(entity);
        var parsed = NumberNormalizer.parse(value);
        if (parsed.isPresent()) {
            setter.accept(parsed.get());
            invoice.fieldConfidences.put(outputField, (double) entity.getConfidence());
        } else {
            invoice.warnings.add("Google Document AI returned an invalid " + displayName(outputField) + ": " + value);
        }
    }

    private EntityValue value(Map<String, Document.Entity> fields, String... names) {
        Document.Entity entity = entity(fields, names);
        return entity == null ? null : new EntityValue(entity, textValue(entity));
    }

    private Document.Entity entity(Map<String, Document.Entity> fields, String... names) {
        for (String name : names) {
            Document.Entity entity = fields.get(name);
            if (entity != null) return entity;
        }
        return null;
    }

    private String text(Map<String, Document.Entity> fields, String... names) {
        Document.Entity entity = entity(fields, names);
        return entity == null ? null : nullIfBlank(textValue(entity));
    }

    private String normalized(Map<String, Document.Entity> fields, String... names) {
        Document.Entity entity = entity(fields, names);
        return entity == null ? null : normalizedValue(entity);
    }

    private BigDecimal amount(Map<String, Document.Entity> fields, String... names) {
        return NumberNormalizer.parse(normalized(fields, names)).orElse(null);
    }

    private Integer integer(Map<String, Document.Entity> fields, String... names) {
        BigDecimal value = amount(fields, names);
        if (value == null) return null;
        try { return value.intValueExact(); } catch (ArithmeticException ignored) { return null; }
    }

    private Quantity quantity(String value) {
        if (value == null) return new Quantity(null, null);
        String compact = value.replaceAll("\\s+", "");
        String[] parts = compact.split("\\+", 2);
        BigDecimal paid = NumberNormalizer.parse(parts[0]).orElse(null);
        BigDecimal free = parts.length == 2 ? NumberNormalizer.parse(parts[1]).orElse(null) : null;
        return new Quantity(paid, free);
    }

    private String textValue(Document.Entity entity) {
        String mention = entity.getMentionText();
        if (mention != null && !mention.isBlank()) return mention.trim();
        return normalizedValue(entity);
    }

    private String normalizedValue(Document.Entity entity) {
        if (entity.hasNormalizedValue() && !entity.getNormalizedValue().getText().isBlank()) {
            return entity.getNormalizedValue().getText().trim();
        }
        return entity.getMentionText() == null ? "" : entity.getMentionText().trim();
    }

    private List<Document.Entity> flatten(List<Document.Entity> entities) {
        var result = new ArrayList<Document.Entity>();
        for (Document.Entity entity : entities) {
            result.add(entity);
            result.addAll(flatten(entity.getPropertiesList()));
        }
        return result;
    }

    private TextToken confidenceToken(Document.Entity entity) {
        int page = entity.hasPageAnchor() && entity.getPageAnchor().getPageRefsCount() > 0
                ? (int) entity.getPageAnchor().getPageRefs(0).getPage() + 1 : 1;
        return new TextToken(textValue(entity), page, 0, 0, 0, 0, entity.getConfidence());
    }

    private DocumentType documentType(String value) {
        if (value == null) return DocumentType.INVOICE;
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return switch (normalized) {
            case "PURCHASE_ORDER", "PO" -> DocumentType.PURCHASE_ORDER;
            case "SALES_ORDER", "SALE_ORDER", "SO" -> DocumentType.SALES_ORDER;
            case "PURCHASE_BILL" -> DocumentType.PURCHASE_BILL;
            case "INVOICE", "TAX_INVOICE" -> DocumentType.INVOICE;
            default -> DocumentType.UNKNOWN;
        };
    }

    private String normalizeType(String type) {
        if (type == null) return "";
        int separator = Math.max(type.lastIndexOf('/'), type.lastIndexOf('.'));
        String value = separator >= 0 ? type.substring(separator + 1) : type;
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String camelCase(String value) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char character : value.toCharArray()) {
            if (character == '_') { upper = true; continue; }
            result.append(upper ? Character.toUpperCase(character) : character);
            upper = false;
        }
        return result.toString();
    }

    private String displayName(String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record EntityValue(Document.Entity entity, String value) { }
    private record Quantity(BigDecimal paid, BigDecimal free) { }
}
