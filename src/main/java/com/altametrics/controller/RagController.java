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

    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@RequestBody AskRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ragService.ask(request));
    }

    @GetMapping("/ask")
    public ResponseEntity<AskResponse> askGet(@RequestParam("q") String question) {
        AskRequest request = new AskRequest(question, 5, null);
        return ResponseEntity.ok(ragService.ask(request));
    }
}
