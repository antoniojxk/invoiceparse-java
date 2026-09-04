package com.invoiceparse.extract;

import com.invoiceparse.api.LineItemResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class LineItemExtractor {
    private static final Pattern HEADER_DESCRIPTION = Pattern.compile("(?i)\\b(description|product|item|particulars)\\b");
    private static final Pattern HEADER_QUANTITY = Pattern.compile("(?i)\\b(qty|quantity)\\b");
    private static final Pattern HEADER_TOTAL = Pattern.compile("(?i)\\b(amount|total|value)\\b");
    private static final Pattern SERIAL_HEADER = Pattern.compile("(?i)\\b(?:s\\.?\\s*no\\.?|sr\\.?\\s*no\\.?)\\b");
    private static final Pattern SERIAL_ROW = Pattern.compile("^(\\d+)\\s+(\\d+(?:\\.\\d+)?(?:\\+\\d+(?:\\.\\d+)?)?)\\s+(.+)$");
    private static final Pattern PACK = Pattern.compile("(?i)^\\d+\\s*[x*]\\s*\\d+[A-Z]*$");
    private static final Pattern MONTH_YEAR = Pattern.compile("^(?:0?[1-9]|1[0-2])[-/]\\d{4}$");
    private static final Pattern TOTAL_LINE = Pattern.compile(
            "(?i)^(?:class\\b|sub\\s*total|taxable|cgst|sgst|igst|grand\\s*total|total\\s*tax|amount\\s*due|gst\\s+\\d)");

    public List<LineItemResponse> extract(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String header = lines.get(i);
            if (!isTableHeader(header)) continue;
            if (SERIAL_HEADER.matcher(header).find()) {
                var structured = parseSerialNumberedRows(lines, i + 1, ClientTableSchema.from(header));
                if (!structured.isEmpty()) return structured;
            }
            var columns = columns(header);
            var result = parseRows(lines, i + 1, columns);
            if (!result.isEmpty()) return result;
        }
        return List.of();
    }

    private boolean isTableHeader(String line) {
        return HEADER_DESCRIPTION.matcher(line).find()
                && HEADER_QUANTITY.matcher(line).find()
                && HEADER_TOTAL.matcher(line).find();
    }

    private List<LineItemResponse> parseSerialNumberedRows(List<String> lines, int start, ClientTableSchema schema) {
        var items = new ArrayList<LineItemResponse>();
        for (int i = start; i < lines.size() && items.size() < 200; i++) {
            String line = lines.get(i);
            if (TOTAL_LINE.matcher(line).find()) break;
            String candidate = line;
            var matcher = SERIAL_ROW.matcher(candidate);
            if (!matcher.matches()) {
                if (!items.isEmpty()) break;
                continue;
            }
            var item = parseSerialNumberedRow(
                    Integer.parseInt(matcher.group(1)), matcher.group(2), matcher.group(3), schema);
            if (item == null) {
                candidate = sanitizeWatermarkText(candidate);
                matcher = SERIAL_ROW.matcher(candidate);
                if (matcher.matches()) {
                    item = parseSerialNumberedRow(
                            Integer.parseInt(matcher.group(1)), matcher.group(2), matcher.group(3), schema);
                }
            }
            if (item == null) {
                for (int j = i + 1; j < Math.min(lines.size(), i + 5); j++) {
                    if (TOTAL_LINE.matcher(lines.get(j)).find() || SERIAL_ROW.matcher(lines.get(j)).matches()) break;
                    candidate = sanitizeWatermarkText(candidate + " " + lines.get(j));
                    matcher = SERIAL_ROW.matcher(candidate);
                    if (!matcher.matches()) continue;
                    item = parseSerialNumberedRow(
                            Integer.parseInt(matcher.group(1)), matcher.group(2), matcher.group(3), schema);
                    if (item != null) {
                        i = j;
                        break;
                    }
                }
            }
            if (item == null) break;
            items.add(item);
        }
        return items;
    }

    private String sanitizeWatermarkText(String value) {
        return value
                .replaceAll("(?i)\\b(?:PHAR(?:MA)?|DISTTRIBUTIO[N]?|DISTRIBUTION|PVT|LTD)\\b", " ")
                .replaceAll("(\\d{4,6})[A-Z]{1,5}(\\d[\\d,.]*\\.\\d+)", "$1 $2")
                .replaceAll("\\b[A-Z]{1,3}(?=\\d[\\d,.]*\\.\\d+)", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private LineItemResponse parseSerialNumberedRow(
            int serialNumber, String quantityText, String remainder, ClientTableSchema schema) {
        String[] tokens = remainder.trim().split("\\s+");
        int cursor = 0;
        String pack = null;
        if (schema.hasPack && tokens.length > 0 && PACK.matcher(tokens[0]).matches()) {
            pack = tokens[0].replace('x', '*').replace('X', '*');
            cursor++;
        }
        if (tokens.length - cursor <= schema.tail.size()) return null;

        int tailStart = tokens.length - schema.tail.size();
        Map<String, BigDecimal> values = new HashMap<>();
        String hsn = null;
        for (int i = 0; i < schema.tail.size(); i++) {
            String field = schema.tail.get(i);
            String raw = tokens[tailStart + i];
            if (field.equals("hsn")) {
                if (!raw.matches("[A-Za-z0-9./-]+")) return null;
                hsn = raw;
            } else {
                BigDecimal value = num(raw);
                if (value == null) return null;
                values.put(field, value);
            }
        }

        int descriptionEnd = tailStart;
        String batch = null;
        String expiryText = null;
        if (descriptionEnd - cursor >= 2 && MONTH_YEAR.matcher(tokens[descriptionEnd - 1]).matches()) {
            expiryText = tokens[descriptionEnd - 1];
            batch = tokens[descriptionEnd - 2];
            descriptionEnd -= 2;
        }
        if (descriptionEnd <= cursor) return null;
        String description = String.join(" ", Arrays.copyOfRange(tokens, cursor, descriptionEnd));

        String[] quantityParts = quantityText.split("\\+", 2);
        BigDecimal quantity = num(quantityParts[0]);
        BigDecimal freeQuantity = quantityParts.length == 2 ? num(quantityParts[1]) : null;
        BigDecimal lineTotal = values.get("total");
        BigDecimal sgstPercentage = values.get("sgstPercentage");
        BigDecimal cgstPercentage = values.get("cgstPercentage");
        BigDecimal igstPercentage = values.get("igstPercentage");
        BigDecimal gstPercentage = igstPercentage;
        if (gstPercentage == null && (sgstPercentage != null || cgstPercentage != null)) {
            gstPercentage = zeroIfNull(sgstPercentage).add(zeroIfNull(cgstPercentage));
        }
        return new LineItemResponse(
                description, description, hsn, batch, null, quantity, null, values.get("rate"),
                null, gstPercentage, lineTotal, lineTotal, 0.90,
                serialNumber, quantityText, freeQuantity, pack, expiryText, values.get("mrp"),
                values.get("discountPercentage"), sgstPercentage, values.get("sgstAmount"),
                cgstPercentage, values.get("cgstAmount"), igstPercentage, values.get("igstAmount"));
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private List<Column> columns(String header) {
        var columns = new ArrayList<Column>();
        add(columns, header, "description", "description|product(?:\\s*name)?|item|particulars");
        add(columns, header, "hsn", "hsn(?:/sac)?|sac");
        add(columns, header, "batch", "batch(?:\\s*no)?");
        add(columns, header, "expiry", "exp(?:iry)?(?:\\s*date)?");
        add(columns, header, "quantity", "qty|quantity");
        add(columns, header, "unit", "uom|unit");
        add(columns, header, "rate", "unit\\s*(?:rate|price)|rate|price");
        add(columns, header, "discount", "disc(?:ount)?");
        add(columns, header, "gst", "gst(?:\\s*%)?|tax\\s*%");
        add(columns, header, "taxable", "taxable(?:\\s*(?:amount|value))?");
        add(columns, header, "total", "line\\s*total|amount|total|value");
        columns.sort(Comparator.comparingInt(Column::start));
        return columns;
    }

    private void add(List<Column> columns, String header, String name, String regex) {
        var matcher = Pattern.compile("(?i)\\b(?:" + regex + ")\\b").matcher(header);
        if (matcher.find() && columns.stream().noneMatch(c -> c.start == matcher.start())) {
            columns.add(new Column(name, matcher.start()));
        }
    }

    private List<LineItemResponse> parseRows(List<String> lines, int start, List<Column> columns) {
        var items = new ArrayList<LineItemResponse>();
        int misses = 0;
        for (int i = start; i < lines.size() && items.size() < 200 && misses < 2; i++) {
            String line = lines.get(i);
            if (TOTAL_LINE.matcher(line).find()) break;
            LineItemResponse item = parseDelimited(line, columns);
            if (item == null) {
                misses++;
                continue;
            }
            misses = 0;
            items.add(item);
        }
        return items;
    }

    private LineItemResponse parseDelimited(String line, List<Column> columns) {
        String[] cells = line.contains("|") ? line.split("\\s*\\|\\s*") : line.trim().split("\\s{2,}");
        if (cells.length < 3) return parseByNumericTail(line, columns);
        var values = new HashMap<String, String>();
        if (cells.length == columns.size()) {
            for (int i = 0; i < cells.length; i++) values.put(columns.get(i).name, cells[i].trim());
        } else {
            return parseByNumericTail(line, columns);
        }
        String description = values.get("description");
        BigDecimal quantity = num(values.get("quantity"));
        BigDecimal rate = num(values.get("rate"));
        BigDecimal total = num(values.get("total"));
        if (description == null || description.isBlank() || quantity == null || total == null) return null;
        return new LineItemResponse(description, description, values.get("hsn"), values.get("batch"),
                DateNormalizer.parse(values.get("expiry")).orElse(null), quantity, values.get("unit"), rate,
                num(values.get("discount")), num(values.get("gst")), num(values.get("taxable")), total, 0.82);
    }

    private LineItemResponse parseByNumericTail(String line, List<Column> columns) {
        // This fallback is intentionally limited to the unambiguous Description/Qty/Rate/Total shape.
        // Richer serial-numbered tables are handled separately by an explicit header-derived schema.
        Set<String> names = columns.stream().map(Column::name).collect(java.util.stream.Collectors.toSet());
        if (!names.equals(Set.of("description", "quantity", "rate", "total"))) return null;
        String[] cells = line.trim().split("\\s+");
        if (cells.length < 4) return null;
        var numeric = new ArrayList<Integer>();
        for (int i = 0; i < cells.length; i++) if (NumberNormalizer.parse(cells[i]).isPresent()) numeric.add(i);
        if (numeric.size() < 3) return null;
        int q = numeric.get(numeric.size() - 3);
        int r = numeric.get(numeric.size() - 2);
        int t = numeric.get(numeric.size() - 1);
        if (q == 0 || r != q + 1 || t != r + 1 || t != cells.length - 1) return null;
        BigDecimal quantity = num(cells[q]);
        BigDecimal rate = num(cells[r]);
        BigDecimal total = num(cells[t]);
        if (quantity == null || rate == null || total == null
                || quantity.signum() < 0 || rate.signum() < 0 || total.signum() < 0) return null;
        if (quantity.multiply(rate).subtract(total).abs().compareTo(new BigDecimal("0.10")) > 0) return null;
        String description = String.join(" ", Arrays.copyOfRange(cells, 0, q));
        return new LineItemResponse(description, description, null, null, null, quantity, null,
                rate, null, null, null, total, 0.65);
    }

    private BigDecimal num(String value) {
        return NumberNormalizer.parse(value).orElse(null);
    }

    private record Column(String name, int start) { }

    private record ClientTableSchema(boolean hasPack, List<String> tail) {
        static ClientTableSchema from(String header) {
            String normalized = header.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
            boolean hasPack = Pattern.compile("\\bPACK\\b").matcher(normalized).find();
            if (normalized.contains("SGST%") && normalized.contains("CGST%")) {
                return new ClientTableSchema(hasPack, List.of("hsn", "rate", "discountPercentage",
                        "sgstPercentage", "sgstAmount", "cgstPercentage", "cgstAmount", "total"));
            }
            if (normalized.contains("IGST%")) {
                return new ClientTableSchema(hasPack, List.of("hsn", "rate", "discountPercentage",
                        "igstPercentage", "igstAmount", "total"));
            }
            if (normalized.matches(".*\\bSGST\\b.*\\bCGST\\b.*")) {
                return new ClientTableSchema(hasPack, List.of("hsn", "rate", "discountPercentage",
                        "sgstAmount", "cgstAmount", "total"));
            }
            if (normalized.matches(".*\\bMRP\\b.*")) {
                return new ClientTableSchema(hasPack,
                        List.of("hsn", "mrp", "rate", "discountPercentage", "total"));
            }
            return new ClientTableSchema(hasPack,
                    List.of("hsn", "rate", "discountPercentage", "total"));
        }
    }
}
