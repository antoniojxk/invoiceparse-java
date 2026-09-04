package com.invoiceparse.extract;

import com.invoiceparse.model.ExtractedContent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class InvoiceFieldExtractor {
    private static final Pattern GSTIN = Pattern.compile("(?i)\\b[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]\\b");
    private static final Pattern DATE = Pattern.compile("(?i)\\b(?:\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}|\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}[- ](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[- ]\\d{2,4})\\b");
    private final LineItemExtractor lineItemExtractor;

    public InvoiceFieldExtractor(LineItemExtractor lineItemExtractor) { this.lineItemExtractor = lineItemExtractor; }

    public ParsedInvoice extract(ExtractedContent content) {
        var invoice = new ParsedInvoice();
        List<String> lines = normalize(content.text());
        invoice.invoiceNumber = labeled(lines, "invoice\\s*(?:number|no\\.?|#)|bill\\s*(?:number|no\\.?)|document\\s*(?:number|no\\.?)")
                .map(this::cleanIdentifier).orElse(null);
        invoice.invoiceDate = labeled(lines, "invoice\\s*date|bill\\s*date|date")
                .flatMap(this::dateWithin).orElseGet(() -> lines.stream().limit(15).map(this::dateWithin)
                        .flatMap(Optional::stream).findFirst().orElse(null));

        List<String> gstins = new ArrayList<>();
        for (String line : lines) {
            var matcher = GSTIN.matcher(line);
            while (matcher.find() && !gstins.contains(matcher.group().toUpperCase(Locale.ROOT))) gstins.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        invoice.supplierGstin = labeled(lines, "(?:supplier|seller|vendor)\\s*gstin|gstin\\s*(?:of\\s*)?(?:supplier|seller|vendor)")
                .flatMap(this::gstinWithin).orElse(gstins.isEmpty() ? null : gstins.get(0));
        invoice.customerGstin = labeled(lines, "(?:customer|buyer|recipient|bill\\s*to)\\s*gstin|gstin\\s*(?:of\\s*)?(?:customer|buyer)")
                .flatMap(this::gstinWithin).orElse(gstins.size() > 1 ? gstins.get(1) : null);
        invoice.supplierName = labeled(lines, "supplier\\s*name|seller\\s*name|vendor\\s*name|supplier(?!\\s*gstin)|seller(?!\\s*gstin)|vendor(?!\\s*gstin)|from")
                .map(this::cleanText).orElseGet(() -> inferSupplier(lines));
        invoice.customerName = labeled(lines, "customer\\s*name|buyer\\s*name|customer(?!\\s*gstin)|buyer(?!\\s*gstin)|bill\\s*to|recipient")
                .map(this::cleanText).orElse(null);
        invoice.address = labeled(lines, "(?:supplier\\s*)?address|registered\\s*office").map(this::cleanText).orElse(null);

        invoice.subtotal = amount(lines, "sub[ -]?total|gross\\s*amount");
        invoice.discount = amount(lines, "(?:total\\s*)?discount");
        invoice.cgst = amount(lines, "cgst(?:\\s*amount)?");
        invoice.sgst = amount(lines, "sgst(?:\\s*amount)?");
        invoice.igst = amount(lines, "igst(?:\\s*amount)?");
        invoice.taxableAmount = amount(lines, "taxable\\s*(?:amount|value)|net\\s*taxable");
        invoice.roundOff = amount(lines, "round(?:ing)?[ -]?off");
        invoice.grandTotal = amount(lines, "grand\\s*total|invoice\\s*(?:total|value)|amount\\s*(?:due|payable)|net\\s*amount");
        if (content.text().matches("(?is).*\\b(?:USD|US\\$|\\$)\\b.*")) invoice.currency = "USD";
        else if (content.text().matches("(?is).*\\bEUR\\b.*|.*€.*")) invoice.currency = "EUR";
        else if (content.text().matches("(?is).*\\bGBP\\b.*|.*£.*")) invoice.currency = "GBP";
        invoice.lineItems = lineItemExtractor.extract(lines);
        double fieldConfidence = content.sourceType() == com.invoiceparse.model.SourceType.DIGITAL_PDF ? 0.92 :
                Math.max(0.50, content.tokens().stream().mapToDouble(t -> t.confidence()).average().orElse(0.70));
        putConfidence(invoice, "invoiceNumber", invoice.invoiceNumber, fieldConfidence);
        putConfidence(invoice, "invoiceDate", invoice.invoiceDate, fieldConfidence);
        putConfidence(invoice, "supplierName", invoice.supplierName, fieldConfidence - 0.05);
        putConfidence(invoice, "supplierGstin", invoice.supplierGstin, fieldConfidence);
        putConfidence(invoice, "customerName", invoice.customerName, fieldConfidence - 0.05);
        putConfidence(invoice, "customerGstin", invoice.customerGstin, fieldConfidence);
        putConfidence(invoice, "address", invoice.address, fieldConfidence - 0.08);
        putConfidence(invoice, "subtotal", invoice.subtotal, fieldConfidence);
        putConfidence(invoice, "discount", invoice.discount, fieldConfidence);
        putConfidence(invoice, "cgst", invoice.cgst, fieldConfidence);
        putConfidence(invoice, "sgst", invoice.sgst, fieldConfidence);
        putConfidence(invoice, "igst", invoice.igst, fieldConfidence);
        putConfidence(invoice, "taxableAmount", invoice.taxableAmount, fieldConfidence);
        putConfidence(invoice, "roundOff", invoice.roundOff, fieldConfidence);
        putConfidence(invoice, "grandTotal", invoice.grandTotal, fieldConfidence);
        long present = Arrays.asList(invoice.invoiceNumber, invoice.invoiceDate, invoice.supplierName,
                invoice.grandTotal).stream().filter(v -> v != null).count();
        invoice.extractionConfidence = Math.min(0.96, 0.45 + present * 0.10 + (invoice.lineItems.isEmpty() ? 0 : 0.10));
        if (invoice.invoiceNumber == null) invoice.warnings.add("Invoice number could not be extracted");
        if (invoice.invoiceDate == null) invoice.warnings.add("Invoice date could not be extracted");
        if (invoice.grandTotal == null) invoice.warnings.add("Grand total could not be extracted");
        if (invoice.lineItems.isEmpty()) invoice.warnings.add("No line items could be extracted");
        return invoice;
    }

    private void putConfidence(ParsedInvoice invoice, String field, Object value, double confidence) {
        if (value != null) invoice.fieldConfidences.put(field, Math.round(Math.max(0, confidence) * 100.0) / 100.0);
    }

    private List<String> normalize(String text) {
        if (text == null) return List.of();
        return text.replace('\u00a0', ' ').replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "")
                .lines().map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private Optional<String> labeled(List<String> lines, String aliases) {
        Pattern pattern = Pattern.compile("(?i)^\\s*(?:" + aliases + ")\\s*(?:[:#=-]|\\s)\\s*(.+?)\\s*$");
        for (String line : lines) {
            var matcher = pattern.matcher(line);
            if (matcher.find() && !matcher.group(1).isBlank()) return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private BigDecimal amount(List<String> lines, String aliases) {
        Pattern label = Pattern.compile("(?i)\\b(?:" + aliases + ")\\b");
        Pattern number = Pattern.compile("[-(]?(?:₹|Rs\\.?|INR|USD|EUR|GBP|[$€£])?\\s*\\d[\\d,]*(?:\\.\\d+)?\\)?", Pattern.CASE_INSENSITIVE);
        BigDecimal result = null;
        for (String line : lines) {
            if (!label.matcher(line).find()) continue;
            var matcher = number.matcher(line);
            while (matcher.find()) result = NumberNormalizer.parse(matcher.group()).orElse(result);
        }
        return result;
    }

    private Optional<java.time.LocalDate> dateWithin(String value) {
        var matcher = DATE.matcher(value);
        while (matcher.find()) {
            var parsed = DateNormalizer.parse(matcher.group());
            if (parsed.isPresent()) return parsed;
        }
        return Optional.empty();
    }

    private Optional<String> gstinWithin(String value) {
        var matcher = GSTIN.matcher(value);
        return matcher.find() ? Optional.of(matcher.group().toUpperCase(Locale.ROOT)) : Optional.empty();
    }

    private String cleanIdentifier(String value) { return value.split("\\s{2,}|\\s+(?:Date|GSTIN)\\b", 2)[0].trim(); }
    private String cleanText(String value) { return value.replaceAll("(?i)\\s+(?:GSTIN|Address)\\s*[:#].*$", "").trim(); }

    private String inferSupplier(List<String> lines) {
        for (String line : lines.stream().limit(8).toList()) {
            if (line.length() >= 3 && line.length() <= 100 && !line.matches("(?i).*(invoice|tax invoice|gstin|date|original).*")) return line;
        }
        return null;
    }
}
