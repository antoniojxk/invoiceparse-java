CREATE TABLE processed_document (
    id UUID PRIMARY KEY,
    file_hash VARCHAR(64) NOT NULL UNIQUE,
    original_filename VARCHAR(512) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_processed_document_created_at ON processed_document (created_at);
