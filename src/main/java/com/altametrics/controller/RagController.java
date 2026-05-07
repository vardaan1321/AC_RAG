package com.altametrics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.altametrics.model.AskRequest;
import com.altametrics.model.AskResponse;
import com.altametrics.service.RagService;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    /**
     * POST /api/rag/ask
     * Ask a question — retrieves relevant chunks from Qdrant and answers via Ollama
     *
     * Request body:
     * {
     *   "question": "What is the refund policy?",
     *   "topK": 5
     * }
     *
     * Example:
     *   curl -X POST http://localhost:8080/api/rag/ask \
     *        -H "Content-Type: application/json" \
     *        -d '{"question": "What is the refund policy?"}'
     */
    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@RequestBody AskRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ragService.ask(request));
    }

    /**
     * GET /api/rag/ask?q=your+question
     * Convenient GET version for quick testing in browser
     *
     * Example:
     *   http://localhost:8080/api/rag/ask?q=What+is+the+refund+policy
     */
    @GetMapping("/ask")
    public ResponseEntity<AskResponse> askGet(@RequestParam("q") String question) {
        AskRequest request = new AskRequest(question, 5);
        return ResponseEntity.ok(ragService.ask(request));
    }
}
