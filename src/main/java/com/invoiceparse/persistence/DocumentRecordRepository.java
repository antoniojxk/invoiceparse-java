package com.invoiceparse.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRecordRepository extends JpaRepository<DocumentRecord, UUID> {
    Optional<DocumentRecord> findByFileHash(String fileHash);
}
