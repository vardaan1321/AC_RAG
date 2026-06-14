package com.altametrics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AskResponse {
    private String question;
    private String answer;
    private List<String> sourcesUsed;
    private long processingTimeMs;
}
