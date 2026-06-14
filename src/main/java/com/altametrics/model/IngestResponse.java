package com.altametrics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IngestResponse {
    private String fileName;
    private int chunksStored;
    private String status;
    private String message;
}
