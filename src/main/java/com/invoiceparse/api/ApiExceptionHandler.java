package com.invoiceparse.api;

import com.invoiceparse.exception.DocumentProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DocumentProcessingException.class)
    ResponseEntity<ApiError> processing(DocumentProcessingException ex, HttpServletRequest request) {
        var status = switch (ex.getCode()) {
            case "UNSUPPORTED_FILE_TYPE", "INVALID_FILE", "DOCUMENT_LIMIT_EXCEEDED" -> HttpStatus.BAD_REQUEST;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "DEMO_BUSY" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        var response = ResponseEntity.status(status);
        if (status == HttpStatus.TOO_MANY_REQUESTS || status == HttpStatus.SERVICE_UNAVAILABLE) {
            response.header(HttpHeaders.RETRY_AFTER, "60");
        }
        return response.body(ApiError.of(status.value(), ex.getCode(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MaxUploadSizeExceededException.class})
    ResponseEntity<ApiError> upload(Exception ex, HttpServletRequest request) {
        var status = HttpStatus.BAD_REQUEST;
        String code = ex instanceof MaxUploadSizeExceededException ? "FILE_TOO_LARGE" : "MISSING_FILE";
        return ResponseEntity.badRequest().body(ApiError.of(status.value(), code, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(404, "NOT_FOUND",
                "The requested resource was not found", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.internalServerError().body(ApiError.of(500, "INTERNAL_ERROR",
                "The document could not be processed", request.getRequestURI()));
    }
}
