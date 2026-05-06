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

    // Lưu ID thay vì Object để tránh lỗi Lazy Loading khi chạy Async (Bất đồng bộ)
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "scan_type", length = 50)
    private String scanType; // Ví dụ: EMOTION_FACE, LIVENESS_VOICE

    @Column(name = "result_label", length = 50)
    private String resultLabel; // HAPPY, FEAR...

    @Column(name = "confidence_score")
    private double confidenceScore;

    @Column(name = "process_time_ms")
    private double processTimeMs;

    // Lưu nguyên cục JSON {ANGRY: 5.0, HAPPY: 43.15} vào đây
    @Column(name = "emotion_details", columnDefinition = "TEXT")
    private String emotionDetails;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}