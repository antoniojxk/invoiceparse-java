package com.invoiceparse.exception;

public class DocumentProcessingException extends RuntimeException {
    private final String code;
    public DocumentProcessingException(String code, String message) { super(message); this.code = code; }
    public DocumentProcessingException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public String getCode() { return code; }
}
