package com.invoiceparse.api;

import com.invoiceparse.model.DocumentType;
import com.invoiceparse.model.SourceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ParseDocumentResponse(
        UUID documentId, String originalFilename, String fileHash, boolean duplicate,
        DocumentType documentType, SourceType sourceType, int pageCount,
        String invoiceNumber, LocalDate invoiceDate, String supplierName, String supplierGstin,
        String customerName, String customerGstin, String address, BigDecimal subtotal,
        BigDecimal discount, BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
        BigDecimal taxableAmount, BigDecimal roundOff, BigDecimal grandTotal, String currency,
        List<LineItemResponse> lineItems, List<ValidationResultResponse> validationResults, Map<String, Double> fieldConfidences,
        double overallConfidence, boolean manualReviewRequired, List<String> warnings
) {
    public ParseDocumentResponse withIdentity(UUID id, String filename, String hash, boolean isDuplicate) {
        return new ParseDocumentResponse(id, filename, hash, isDuplicate, documentType, sourceType, pageCount,
                invoiceNumber, invoiceDate, supplierName, supplierGstin, customerName, customerGstin, address,
                subtotal, discount, cgst, sgst, igst, taxableAmount, roundOff, grandTotal, currency,
                lineItems, validationResults, fieldConfidences, overallConfidence, manualReviewRequired, warnings);
    }
}
