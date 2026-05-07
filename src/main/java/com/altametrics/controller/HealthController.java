package com.altametrics.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of(
            "app", "Spring Boot RAG",
            "status", "running",
            "endpoints", "POST /api/documents/ingest | POST /api/rag/ask | GET /api/rag/ask?q="
        );
    }
}
