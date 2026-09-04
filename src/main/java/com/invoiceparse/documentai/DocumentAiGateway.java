package com.invoiceparse.documentai;

import com.google.cloud.documentai.v1.Document;

public interface DocumentAiGateway {
    Document process(byte[] content, String mimeType);
}
