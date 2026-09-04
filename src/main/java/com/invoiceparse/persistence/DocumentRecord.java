package com.invoiceparse.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_document")
public class DocumentRecord {
    @Id private UUID id;
    @Column(name = "file_hash", nullable = false, unique = true, length = 64) private String fileHash;
    @Column(name = "original_filename", nullable = false, length = 512) private String originalFilename;
    @Column(name = "response_json", nullable = false, columnDefinition = "text") private String responseJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected DocumentRecord() { }
    public DocumentRecord(UUID id, String fileHash, String originalFilename, String responseJson, Instant createdAt) {
        this.id = id; this.fileHash = fileHash; this.originalFilename = originalFilename;
        this.responseJson = responseJson; this.createdAt = createdAt;
    }
    public UUID getId() { return id; }
    public String getFileHash() { return fileHash; }
    public String getOriginalFilename() { return originalFilename; }
    public String getResponseJson() { return responseJson; }
    public Instant getCreatedAt() { return createdAt; }
}
