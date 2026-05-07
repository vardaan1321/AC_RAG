package com.altametrics.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.altametrics.model.IngestResponse;
import com.altametrics.service.DocumentIngestionService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    /**
     * POST /api/documents/ingest
     * Upload a single document (PDF, DOCX, TXT, HTML...)
     *
     * Example:
     *   curl -X POST http://localhost:8080/api/documents/ingest \
     *        -F "file=@yourfile.pdf"
     */
    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingestDocument(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                new IngestResponse(null, 0, "ERROR", "File is empty")
            );
        }

        try {
            IngestResponse response = ingestionService.ingest(file);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to ingest file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body(
                new IngestResponse(file.getOriginalFilename(), 0, "ERROR", e.getMessage())
            );
        }
    }

    /**
     * POST /api/documents/ingest/batch
     * Upload multiple documents at once
     *
     * Example:
     *   curl -X POST http://localhost:8080/api/documents/ingest/batch \
     *        -F "files=@file1.pdf" -F "files=@file2.docx"
     */
    @PostMapping("/ingest/batch")
    public ResponseEntity<List<IngestResponse>> ingestBatch(
            @RequestParam("files") List<MultipartFile> files) {

        List<IngestResponse> results = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                results.add(ingestionService.ingest(file));
            } catch (IOException e) {
                log.error("Failed to ingest: {}", file.getOriginalFilename(), e);
                results.add(new IngestResponse(
                    file.getOriginalFilename(), 0, "ERROR", e.getMessage()
                ));
            }
        }

        return ResponseEntity.ok(results);
    }
}
