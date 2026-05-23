package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_scan_logs")
@Data
public class AiScanLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "scan_type", length = 50)
    private String scanType;  

    @Column(name = "result_label", length = 50)
    private String resultLabel;  

    @Column(name = "confidence_score")
    private double confidenceScore;

    @Column(name = "process_time_ms")
    private double processTimeMs;

 
    @Column(name = "emotion_details", columnDefinition = "TEXT")
    private String emotionDetails;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}