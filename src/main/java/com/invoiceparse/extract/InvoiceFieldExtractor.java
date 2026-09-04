package com.invoiceparse.extract;

import com.invoiceparse.model.ExtractedContent;
import com.invoiceparse.model.DocumentType;
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
    private static final Pattern DOCUMENT_NUMBER = Pattern.compile(
            "(?i)\\b(?:invoice|bill|document|order)\\s*(?:number|no\\.?|#)\\s*(?::|#|=|-)?\\s*([A-Z0-9][A-Z0-9./_-]*)");
    private static final Pattern ROLE_TITLE = Pattern.compile(
            "(?i)\\b(PURCHASE\\s+BILL|PURCHASE\\s+ORDER|SALE(?:S)?\\s+ORDER)\\b");
    private static final Pattern PARTY_NAME = Pattern.compile("(?i)\\bParty\\s+Name\\b");
    private static final Pattern COMPANY_MARKER = Pattern.compile(
            "(?i)(?:\\bPVT\\.?\\s*LTD\\.?|\\bPRIVATE\\s+LIMITED\\b|\\bPHARMACY\\b)");
    private static final Pattern STATE_CODE = Pattern.compile("(?i)\\bState\\s*Code\\s*(?::|=|-)?\\s*(\\d{2})\\b");
    private static final Pattern TABLE_HEADER = Pattern.compile("(?i).*\\b(?:description|product|item|particulars)\\b.*\\b(?:qty|quantity)\\b.*\\b(?:amount|total|value)\\b.*");
    private static final Pattern SUMMARY_LABEL = Pattern.compile(
            "(?i)\\b(?:sub[ -]?total|gross\\s*amount|bill\\s*dis(?:count)?|total\\s*discount|discount|cgst(?:\\s*amount)?|sgst(?:\\s*amount)?|igst(?:\\s*amount)?|taxable\\s*(?:amount|value)|net\\s*taxable|round(?:ing)?[ -]?off|grand\\s*total|invoice\\s*(?:total|value)|amount\\s*(?:due|payable)|net\\s*amount|total\\s*gst|t\\.\\s*items|t\\.\\s*qty)\\b");
    private static final Pattern GST_CONTEXT = Pattern.compile("(?i)\\bGST\\s*INVOICE\\b");
    private static final Pattern CUSTOMER_GST_LABEL = Pattern.compile("(?i)\\b(?:customer|buyer|recipient|bill\\s*to)\\s*GSTI[NM]\\b");
    private final LineItemExtractor lineItemExtractor;

    public InvoiceFieldExtractor(LineItemExtractor lineItemExtractor) { this.lineItemExtractor = lineItemExtractor; }

    public ParsedInvoice extract(ExtractedContent content) {
        var invoice = new ParsedInvoice();
        List<String> lines = normalize(content.text());
        invoice.documentType = classify(content.text());
        invoice.invoiceNumber = documentNumber(lines).orElse(null);
        invoice.invoiceDate = labeledAnywhere(lines, "invoice\\s*date|bill\\s*date|document\\s*date|order\\s*date")
                .flatMap(this::dateWithin).orElseGet(() -> lines.stream().limit(18).map(this::dateWithin)
                        .flatMap(Optional::stream).findFirst().orElse(null));

        List<String> gstins = new ArrayList<>();
        for (String line : lines) {
            var matcher = GSTIN.matcher(line);
            while (matcher.find() && !gstins.contains(matcher.group().toUpperCase(Locale.ROOT))) gstins.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        var parties = inferParties(lines, gstins, invoice.documentType);
        invoice.supplierGstin = labeled(lines, "(?:supplier|seller|vendor)\\s*gstin|gstin\\s*(?:of\\s*)?(?:supplier|seller|vendor)")
                .flatMap(this::gstinWithin).orElse(parties.supplierGstin());
        invoice.customerGstin = labeled(lines, "(?:customer|buyer|recipient|bill\\s*to)\\s*gstin|gstin\\s*(?:of\\s*)?(?:customer|buyer)")
                .flatMap(this::gstinWithin).orElse(parties.customerGstin());
        invoice.supplierName = labeled(lines, "supplier\\s*name|seller\\s*name|vendor\\s*name|supplier(?!\\s*gstin)|seller(?!\\s*gstin)|vendor(?!\\s*gstin)|from")
                .map(this::cleanText).orElse(parties.supplierName());
        invoice.customerName = labeled(lines, "customer\\s*name|buyer\\s*name|customer(?!\\s*gstin)|buyer(?!\\s*gstin)|bill\\s*to|recipient")
                .map(this::cleanText).orElse(parties.customerName());
        if (invoice.supplierName == null) invoice.supplierName = inferSupplier(lines);
        invoice.address = labeled(lines, "(?:supplier\\s*)?address|registered\\s*office").map(this::cleanText).orElse(null);

        invoice.subtotal = amount(lines, "sub[ -]?total|gross\\s*amount");
        invoice.discount = amount(lines, "bill\\s*dis(?:count)?|(?:total\\s*)?discount");
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
        boolean gstInvoice = GST_CONTEXT.matcher(content.text()).find();
        if (gstInvoice) invoice.expectedFields.add("supplierGstin");
        if (CUSTOMER_GST_LABEL.matcher(content.text()).find()) invoice.expectedFields.add("customerGstin");
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
        for (String expected : invoice.expectedFields) {
            if ((expected.equals("supplierGstin") && invoice.supplierGstin == null)
                    || (expected.equals("customerGstin") && invoice.customerGstin == null)) {
                invoice.fieldConfidences.put(expected, 0.0);
                invoice.warnings.add(displayName(expected) + " was expected but could not be extracted");
            }
        }
        long present = Arrays.asList(invoice.invoiceNumber, invoice.invoiceDate, invoice.supplierName,
                invoice.grandTotal).stream().filter(v -> v != null).count();
        invoice.extractionConfidence = Math.min(0.96, 0.45 + present * 0.10 + (invoice.lineItems.isEmpty() ? 0 : 0.10));
        if (invoice.invoiceNumber == null) invoice.warnings.add("Document number could not be extracted");
        if (invoice.invoiceDate == null) invoice.warnings.add("Document date could not be extracted");
        if (invoice.grandTotal == null) invoice.warnings.add("Grand total could not be extracted");
        if (invoice.lineItems.isEmpty()) invoice.warnings.add("No line items could be extracted");
        return invoice;
    }

    private void putConfidence(ParsedInvoice invoice, String field, Object value, double confidence) {
        if (value != null) invoice.fieldConfidences.put(field, Math.round(Math.max(0, confidence) * 100.0) / 100.0);
    }

    private String displayName(String field) {
        return field.equals("supplierGstin") ? "Supplier GSTIN" : "Customer GSTIN";
    }

    private List<String> normalize(String text) {
        if (text == null) return List.of();
        return text.replace('\u00a0', ' ').replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "")
                .lines().map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private DocumentType classify(String text) {
        if (text == null) return DocumentType.UNKNOWN;
        if (text.matches("(?is).*\\bPURCHASE\\s+BILL\\b.*")) return DocumentType.PURCHASE_BILL;
        if (text.matches("(?is).*\\bPURCHASE\\s+ORDER\\b.*")) return DocumentType.PURCHASE_ORDER;
        if (text.matches("(?is).*\\bSALE(?:S)?\\s+ORDER\\b.*")) return DocumentType.SALES_ORDER;
        if (text.matches("(?is).*\\b(?:TAX\\s+)?INVOICE\\b.*")) return DocumentType.INVOICE;
        return DocumentType.UNKNOWN;
    }

    private Optional<String> documentNumber(List<String> lines) {
        for (String line : lines.stream().limit(25).toList()) {
            var matcher = DOCUMENT_NUMBER.matcher(line);
            while (matcher.find()) {
                String value = matcher.group(1).trim();
                if (!value.equalsIgnoreCase("date") && !value.equalsIgnoreCase("no")) return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private Optional<String> labeled(List<String> lines, String aliases) {
        Pattern pattern = Pattern.compile("(?i)^\\s*(?:" + aliases + ")\\s*(?:[:#=-]|\\s)\\s*(.+?)\\s*$");
        for (String line : lines) {
            var matcher = pattern.matcher(line);
            if (matcher.find() && !matcher.group(1).isBlank()) return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private Optional<String> labeledAnywhere(List<String> lines, String aliases) {
        Pattern pattern = Pattern.compile("(?i)\\b(?:" + aliases + ")\\b\\s*(?:[:#=-]|\\s)\\s*(.+?)\\s*$");
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
            var labels = label.matcher(line);
            int afterLastLabel = -1;
            while (labels.find()) afterLastLabel = labels.end();
            if (afterLastLabel < 0) continue;
            String segment = line.substring(afterLastLabel);
            var nextLabel = SUMMARY_LABEL.matcher(segment);
            if (nextLabel.find()) segment = segment.substring(0, nextLabel.start());
            var matcher = number.matcher(segment);
            BigDecimal candidate = null;
            while (matcher.find()) candidate = NumberNormalizer.parse(matcher.group()).orElse(candidate);
            if (candidate != null) result = candidate;
        }
        return result;
    }

    private PartyFields inferParties(List<String> lines, List<String> gstins, DocumentType type) {
        List<String> header = new ArrayList<>();
        for (String line : lines) {
            if (TABLE_HEADER.matcher(line).matches()) break;
            header.add(line);
        }

        String issuer = null;
        String party = null;
        for (int i = 0; i < header.size(); i++) {
            String line = header.get(i);
            var partyMatcher = PARTY_NAME.matcher(line);
            if (partyMatcher.find()) {
                String prefix = line.substring(0, partyMatcher.start()).trim();
                if (COMPANY_MARKER.matcher(prefix).find()) issuer = cleanCompany(prefix);
                String suffix = line.substring(partyMatcher.end()).replaceFirst("^\\s*(?::|=|-)\\s*", "").trim();
                if (COMPANY_MARKER.matcher(suffix).find()) party = cleanCompany(suffix);
                for (int j = i + 1; party == null && j < Math.min(header.size(), i + 3); j++) {
                    String continuation = header.get(j);
                    int colon = continuation.indexOf(':');
                    String candidate = colon >= 0 ? continuation.substring(colon + 1).trim() : continuation;
                    if (COMPANY_MARKER.matcher(candidate).find()) party = cleanCompany(candidate);
                }
                break;
            }
        }

        if (issuer == null || party == null) {
            for (String line : header) {
                var title = ROLE_TITLE.matcher(line);
                if (!title.find()) continue;
                String prefix = line.substring(0, title.start()).trim();
                String suffix = line.substring(title.end()).trim();
                if (issuer == null && COMPANY_MARKER.matcher(prefix).find()) issuer = cleanCompany(prefix);
                if (party == null && COMPANY_MARKER.matcher(suffix).find()) party = cleanCompany(suffix);
            }
        }

        List<String> companies = new ArrayList<>();
        for (String line : header) {
            if (!COMPANY_MARKER.matcher(line).find() || line.matches("(?i)^For\\s+.*")) continue;
            String candidate = cleanCompany(line);
            if (candidate != null && companies.stream().noneMatch(candidate::equalsIgnoreCase)) companies.add(candidate);
        }
        if (issuer == null && !companies.isEmpty()) issuer = companies.get(0);
        if (party == null) {
            for (String candidate : companies) {
                if (issuer == null || !candidate.equalsIgnoreCase(issuer)) {
                    party = candidate;
                    break;
                }
            }
        }

        String issuerGstin = null;
        String partyGstin = null;
        for (String line : header) {
            if (!line.matches("(?i).*\\bInvoice\\s*(?:No\\.?|Number|#).*")) continue;
            var matcher = GSTIN.matcher(line);
            if (matcher.find()) {
                issuerGstin = matcher.group().toUpperCase(Locale.ROOT);
                break;
            }
        }
        String issuerState = null;
        for (String line : header) {
            var state = STATE_CODE.matcher(line);
            if (state.find()) {
                issuerState = state.group(1);
                break;
            }
        }
        if (issuerGstin == null && issuerState != null) {
            for (String value : gstins) {
                if (value.startsWith(issuerState)) {
                    issuerGstin = value;
                    break;
                }
            }
        }
        if (issuerGstin == null && gstins.size() == 1) issuerGstin = gstins.get(0);
        if (issuerGstin != null) {
            for (String value : gstins) {
                if (!value.equals(issuerGstin)) {
                    partyGstin = value;
                    break;
                }
            }
        } else if (!gstins.isEmpty()) {
            issuerGstin = gstins.get(0);
            if (gstins.size() > 1) partyGstin = gstins.get(1);
        }

        boolean purchase = type == DocumentType.PURCHASE_ORDER || type == DocumentType.PURCHASE_BILL;
        if (purchase) return new PartyFields(party, partyGstin, issuer, issuerGstin);
        return new PartyFields(issuer, issuerGstin, party, partyGstin);
    }

    private String cleanCompany(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("\\s+", " ").trim();
        int colon = cleaned.lastIndexOf(':');
        if (colon >= 0 && COMPANY_MARKER.matcher(cleaned.substring(colon + 1)).find()) {
            cleaned = cleaned.substring(colon + 1).trim();
        }
        var suffix = Pattern.compile("(?i)\\b(?:PVT\\.?\\s*LTD\\.?|PRIVATE\\s+LIMITED)").matcher(cleaned);
        if (suffix.find()) cleaned = cleaned.substring(0, suffix.end()).trim();
        cleaned = cleaned.replaceAll("(?i)^Party\\s+Name\\s*(?::|=|-)?\\s*", "").trim();
        cleaned = cleaned.replaceAll("\\.+$", "");
        return cleaned.isBlank() ? null : cleaned;
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

    private String cleanText(String value) { return value.replaceAll("(?i)\\s+(?:GSTIN|Address)\\s*[:#].*$", "").trim(); }

    private String inferSupplier(List<String> lines) {
        for (String line : lines.stream().limit(8).toList()) {
            if (line.length() >= 3 && line.length() <= 100 && !line.matches("(?i).*(invoice|tax invoice|gstin|date|original).*")) return line;
        }
        return null;
    }

    private record PartyFields(
            String supplierName, String supplierGstin, String customerName, String customerGstin
    ) { }
}
