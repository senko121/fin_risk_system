package com.datn.finrisk.application.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
public class EmotionAIResponse {

    @JsonProperty("emotion")
    private String emotion;

    @JsonProperty("confidence")
    private double confidence;

    // Hứng trọn bộ 7 cảm xúc
    @JsonProperty("prob_details")
    private Map<String, Double> probDetails;

    @JsonProperty("process_time_ms")
    private double processTimeMs;
}