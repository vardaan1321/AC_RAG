package com.altametrics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// ── Ask request ───────────────────────────────────────────────────
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AskRequest {
    private String question;
    private int topK = 5;  // override how many chunks to retrieve
}
