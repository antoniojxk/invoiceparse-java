package com.invoiceparse.api;

import com.invoiceparse.exception.DocumentProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DocumentProcessingException.class)
    ResponseEntity<ApiError> processing(DocumentProcessingException ex, HttpServletRequest request) {
        var status = ex.getCode().equals("UNSUPPORTED_FILE_TYPE") || ex.getCode().equals("INVALID_FILE")
                ? HttpStatus.BAD_REQUEST : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(ApiError.of(status.value(), ex.getCode(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MaxUploadSizeExceededException.class})
    ResponseEntity<ApiError> upload(Exception ex, HttpServletRequest request) {
        var status = HttpStatus.BAD_REQUEST;
        String code = ex instanceof MaxUploadSizeExceededException ? "FILE_TOO_LARGE" : "MISSING_FILE";
        return ResponseEntity.badRequest().body(ApiError.of(status.value(), code, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.internalServerError().body(ApiError.of(500, "INTERNAL_ERROR",
                "The document could not be processed", request.getRequestURI()));
    }
}
