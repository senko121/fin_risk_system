package com.datn.finrisk.application.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class FaceAIResponse {

    @JsonProperty("is_matched")
    private boolean matched;

    @JsonProperty("similarity_distance")
    private double similarityDistance;

    @JsonProperty("threshold")
    private double threshold;

    @JsonProperty("backend_used")
    private String backendUsed;

    @JsonProperty("confidence_band")
    private String confidenceBand;

    // Liveness / anti-spoofing fields — present only when MiniFASNet models are loaded.
    // Null-safe: Java callers must use Boolean.TRUE.equals() / null-check before using.
    @JsonProperty("liveness_pass")
    private Boolean livenessPass;

    @JsonProperty("liveness_score")
    private Double livenessScore;

    @JsonProperty("spoof_detected")
    private Boolean spoofDetected;
}