package com.invoiceparse.validation;

import com.invoiceparse.api.ValidationResultResponse;
import com.invoiceparse.config.InvoiceParseProperties;
import com.invoiceparse.extract.ParsedInvoice;
import com.invoiceparse.model.ExtractedContent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConfidenceAssessor {
    private final double minimumConfidence;

    public ConfidenceAssessor(InvoiceParseProperties properties) {
        this.minimumConfidence = properties.minimumOverallConfidence();
    }

    public Assessment assess(ParsedInvoice invoice, ExtractedContent content,
                             List<ValidationResultResponse> validationResults) {
        double sourceConfidence = content.tokens().stream().mapToDouble(t -> t.confidence()).average().orElse(1.0);
        double confidence = invoice.extractionConfidence * 0.80 + sourceConfidence * 0.20;
        long missingExpected = invoice.expectedFields.stream()
                .filter(field -> invoice.fieldConfidences.getOrDefault(field, 0.0) == 0.0).count();
        long invalid = validationResults.stream().filter(result -> !result.valid()).count();

        confidence -= missingExpected * 0.15;
        confidence -= invalid * 0.12;
        confidence = Math.round(Math.max(0, Math.min(0.99, confidence)) * 100.0) / 100.0;
        boolean manualReview = confidence < minimumConfidence || missingExpected > 0
                || invalid > 0 || !invoice.warnings.isEmpty();
        return new Assessment(confidence, manualReview);
    }

    public record Assessment(double overallConfidence, boolean manualReviewRequired) { }
}
