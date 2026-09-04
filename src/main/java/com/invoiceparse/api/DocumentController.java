package com.invoiceparse.api;

import com.invoiceparse.service.DocumentProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentProcessingService service;
    private final DemoRequestGuard requestGuard;
    public DocumentController(DocumentProcessingService service, DemoRequestGuard requestGuard) {
        this.service = service;
        this.requestGuard = requestGuard;
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Parse an invoice PDF or image")
    public ParseDocumentResponse parse(
            @RequestPart("file") @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(type = "string", format = "binary"))) MultipartFile file,
            HttpServletRequest request) {
        try (var ignored = requestGuard.acquire(request.getRemoteAddr())) {
            return service.process(file);
        }
    }
}
