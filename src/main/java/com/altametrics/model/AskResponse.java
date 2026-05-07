package com.altametrics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// ── Ask response ──────────────────────────────────────────────────
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AskResponse {
    private String question;
    private String answer;
    private List<String> sourcesUsed;   // which chunks were retrieved
    private long processingTimeMs;
}
