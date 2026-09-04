package com.invoiceparse.extract;

import com.invoiceparse.api.LineItemResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class LineItemExtractor {
    private static final Pattern HEADER_DESCRIPTION = Pattern.compile("(?i)\\b(description|product|item|particulars)\\b");
    private static final Pattern HEADER_QUANTITY = Pattern.compile("(?i)\\b(qty|quantity)\\b");
    private static final Pattern HEADER_TOTAL = Pattern.compile("(?i)\\b(amount|total|value)\\b");
    private static final Pattern TOTAL_LINE = Pattern.compile("(?i)^(sub\\s*total|taxable|cgst|sgst|igst|grand\\s*total|total\\s*tax|amount\\s*due)");

    public List<LineItemResponse> extract(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String header = lines.get(i);
            if (HEADER_DESCRIPTION.matcher(header).find() && HEADER_QUANTITY.matcher(header).find() && HEADER_TOTAL.matcher(header).find()) {
                var columns = columns(header);
                var result = parseRows(lines, i + 1, columns);
                if (!result.isEmpty()) return result;
            }
        }
        return List.of();
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
        if (matcher.find() && columns.stream().noneMatch(c -> c.start == matcher.start())) columns.add(new Column(name, matcher.start()));
    }

    private List<LineItemResponse> parseRows(List<String> lines, int start, List<Column> columns) {
        var items = new ArrayList<LineItemResponse>();
        int misses = 0;
        for (int i = start; i < lines.size() && items.size() < 200 && misses < 2; i++) {
            String line = lines.get(i);
            if (TOTAL_LINE.matcher(line).find()) break;
            LineItemResponse item = parseDelimited(line, columns);
            if (item == null) { misses++; continue; }
            misses = 0; items.add(item);
        }
        return items;
    }

    private LineItemResponse parseDelimited(String line, List<Column> columns) {
        String[] cells = line.contains("|") ? line.split("\\s*\\|\\s*") : line.trim().split("\\s{2,}");
        if (cells.length < 3) return parseByNumericTail(line);
        var values = new java.util.HashMap<String, String>();
        if (cells.length == columns.size()) {
            for (int i = 0; i < cells.length; i++) values.put(columns.get(i).name, cells[i].trim());
        } else return parseByNumericTail(line);
        String description = values.get("description");
        BigDecimal quantity = num(values.get("quantity")), rate = num(values.get("rate")), total = num(values.get("total"));
        if (description == null || description.isBlank() || quantity == null || total == null) return null;
        return new LineItemResponse(description, description, values.get("hsn"), values.get("batch"),
                DateNormalizer.parse(values.get("expiry")).orElse(null), quantity, values.get("unit"), rate,
                num(values.get("discount")), num(values.get("gst")), num(values.get("taxable")), total, 0.82);
    }

    private LineItemResponse parseByNumericTail(String line) {
        String[] cells = line.trim().split("\\s+");
        if (cells.length < 4) return null;
        var numeric = new ArrayList<Integer>();
        for (int i = 0; i < cells.length; i++) if (NumberNormalizer.parse(cells[i]).isPresent()) numeric.add(i);
        if (numeric.size() < 3) return null;
        int q = numeric.get(numeric.size() - 3), r = numeric.get(numeric.size() - 2), t = numeric.get(numeric.size() - 1);
        if (q == 0) return null;
        String description = String.join(" ", java.util.Arrays.copyOfRange(cells, 0, q));
        return new LineItemResponse(description, description, null, null, null, num(cells[q]), null,
                num(cells[r]), null, null, null, num(cells[t]), 0.65);
    }

    private BigDecimal num(String value) { return NumberNormalizer.parse(value).orElse(null); }
    private record Column(String name, int start) { }
}
