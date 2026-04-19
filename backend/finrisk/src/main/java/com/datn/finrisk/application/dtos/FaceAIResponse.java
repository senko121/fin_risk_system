package com.datn.finrisk.application.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class FaceAIResponse {

    @JsonProperty("is_matched")
    private boolean matched;   // 🔥 đổi tên field

    @JsonProperty("similarity_distance")
    private double similarityDistance;

    @JsonProperty("threshold")
    private double threshold;
}