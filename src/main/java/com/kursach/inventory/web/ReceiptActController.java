package com.kursach.inventory.web;

import com.kursach.inventory.service.ReceiptActDocumentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.charset.StandardCharsets;

@Controller
public class ReceiptActController {
    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final ReceiptActDocumentService documentService;

    public ReceiptActController(ReceiptActDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping({
            "/warehouse/equipment/receipt-requests/{requestId}/act.docx",
            "/economist/receipt-requests/{requestId}/act.docx"
    })
    public ResponseEntity<byte[]> download(@PathVariable Long requestId) {
        byte[] document = documentService.buildAct(requestId);
        String filename = documentService.filename(requestId);
        return ResponseEntity.ok()
                .contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(document);
    }
}
