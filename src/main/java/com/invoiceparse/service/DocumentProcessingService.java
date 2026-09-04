package com.invoiceparse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceparse.api.ParseDocumentResponse;
import com.invoiceparse.config.InvoiceParseProperties;
import com.invoiceparse.exception.DocumentProcessingException;
import com.invoiceparse.extract.DocumentContentExtractor;
import com.invoiceparse.extract.FileTypeDetector;
import com.invoiceparse.extract.InvoiceFieldExtractor;
import com.invoiceparse.model.DocumentType;
import com.invoiceparse.persistence.DocumentRecord;
import com.invoiceparse.persistence.DocumentRecordRepository;
import com.invoiceparse.validation.InvoiceValidator;
import com.invoiceparse.validation.ConfidenceAssessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentProcessingService {
    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);
    private final FileTypeDetector fileTypes;
    private final DocumentContentExtractor contentExtractor;
    private final InvoiceFieldExtractor invoiceExtractor;
    private final InvoiceValidator validator;
    private final ConfidenceAssessor confidenceAssessor;
    private final DocumentRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final InvoiceParseProperties properties;

    public DocumentProcessingService(FileTypeDetector fileTypes, DocumentContentExtractor contentExtractor,
            InvoiceFieldExtractor invoiceExtractor, InvoiceValidator validator, ConfidenceAssessor confidenceAssessor,
            DocumentRecordRepository repository,
            ObjectMapper objectMapper, InvoiceParseProperties properties) {
        this.fileTypes = fileTypes; this.contentExtractor = contentExtractor; this.invoiceExtractor = invoiceExtractor;
        this.validator = validator; this.confidenceAssessor = confidenceAssessor; this.repository = repository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public synchronized ParseDocumentResponse process(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new DocumentProcessingException("INVALID_FILE", "The uploaded file is empty");
        deleteExpiredResults();
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw new DocumentProcessingException("INVALID_FILE", "The uploaded file could not be read", e); }
        String hash = sha256(bytes);
        var prior = repository.findByFileHash(hash);
        if (prior.isPresent()) {
            log.info("Duplicate document received: documentId={}, filename={}", prior.get().getId(), safeFilename(file.getOriginalFilename()));
            return deserialize(prior.get().getResponseJson()).withIdentity(
                    prior.get().getId(), safeFilename(file.getOriginalFilename()), hash, true);
        }

        var type = fileTypes.detect(bytes);
        var content = contentExtractor.extract(bytes, type);
        var invoice = invoiceExtractor.extract(content);
        var validation = validator.validate(invoice);
        var warnings = new ArrayList<>(invoice.warnings);
        validation.stream().filter(v -> !v.valid()).map(v -> v.message() + " (" + v.field() + ")").forEach(warnings::add);
        var assessment = confidenceAssessor.assess(invoice, content, validation);
        double confidence = assessment.overallConfidence();
        boolean manualReview = assessment.manualReviewRequired();
        DocumentType documentType = invoice.documentType != DocumentType.UNKNOWN
                ? invoice.documentType
                : invoice.invoiceNumber != null || invoice.grandTotal != null ? DocumentType.INVOICE : DocumentType.UNKNOWN;
        UUID id = UUID.randomUUID();
        String filename = safeFilename(file.getOriginalFilename());
        var response = new ParseDocumentResponse(id, filename, hash, false, documentType, content.sourceType(), content.pageCount(),
                invoice.invoiceNumber, invoice.invoiceDate, invoice.supplierName, invoice.supplierGstin,
                invoice.customerName, invoice.customerGstin, invoice.address, invoice.subtotal, invoice.discount,
                invoice.cgst, invoice.sgst, invoice.igst, invoice.taxableAmount, invoice.roundOff, invoice.grandTotal,
                invoice.currency, invoice.lineItems, validation, java.util.Map.copyOf(invoice.fieldConfidences),
                confidence, manualReview, List.copyOf(warnings), invoice.invoiceNumber, invoice.invoiceDate);
        repository.saveAndFlush(new DocumentRecord(id, hash, filename, serialize(response), Instant.now()));
        log.info("Processed document: documentId={}, filename={}, sourceType={}, pages={}, reviewRequired={}",
                id, filename, content.sourceType(), content.pageCount(), manualReview);
        return response;
    }

    private void deleteExpiredResults() {
        if (properties.resultRetentionMinutes() > 0) {
            repository.deleteByCreatedAtBefore(Instant.now().minusSeconds(properties.resultRetentionMinutes() * 60));
        }
    }

    private String serialize(ParseDocumentResponse value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new DocumentProcessingException("PERSISTENCE_ERROR", "The parsing result could not be stored", e); }
    }
    private ParseDocumentResponse deserialize(String value) {
        try { return objectMapper.readValue(value, ParseDocumentResponse.class); }
        catch (JsonProcessingException e) { throw new DocumentProcessingException("PERSISTENCE_ERROR", "A prior parsing result could not be read", e); }
    }
    private String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String safeFilename(String name) {
        if (name == null || name.isBlank()) return "upload";
        String value = name.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "");
        return value.substring(0, Math.min(512, value.length()));
    }

}
