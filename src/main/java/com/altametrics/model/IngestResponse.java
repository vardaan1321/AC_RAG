package com.altametrics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// ── Ingest response ───────────────────────────────────────────────
@Data
@AllArgsConstructor
@NoArgsConstructor
public class IngestResponse {
    private String fileName;
    private int chunksStored;
    private String status;
    private String message;
}
