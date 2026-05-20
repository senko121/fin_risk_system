package com.datn.finrisk.application.dtos;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AdminAiLogDTO {
    private Long id;
    private Long transactionId;
    private Long userId;
    private String userFullName;  
    private String scanType;
    private String resultLabel;
    private Double confidenceScore;
    private Double processTimeMs;
    private String emotionDetails;
    private LocalDateTime createdAt;
}