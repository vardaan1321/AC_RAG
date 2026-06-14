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

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingestDocument(
            @RequestParam() MultipartFile file) {

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

    @PostMapping("/ingest/batch")
    public ResponseEntity<List<IngestResponse>> ingestBatch(
            @RequestParam() List<MultipartFile> files) {

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
