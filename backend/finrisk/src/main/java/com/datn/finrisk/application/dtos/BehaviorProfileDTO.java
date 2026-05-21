package com.datn.finrisk.application.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BehaviorProfileDTO {

    // Thông tin cơ bản — GIỮ NGUYÊN
    private Long userId;
    private int txCount;
    private String profilingPhase;
    private double profileReliability;
    private String calculationMethod;
    private String lastUpdated;

    // Hành vi trung bình — GIỮ NGUYÊN
    private double avgAmount;
    private String avgTransactionHour;
    private double avgGapHours;

    // THÊM: Độ lệch chuẩn để Admin hiểu độ dao động
    private double stdAmount;          // ← THÊM
    private double stdGapHours;        // ← THÊM

    // Điểm tổng — GIỮ NGUYÊN
    private double anomalyScore;
    private String anomalyLevel;

    // THÊM: Breakdown điểm từng chiều cho Radar Chart
    private double amountAnomalyScore;     // ← THÊM: 0-100
    private double hourAnomalyScore;       // ← THÊM: 0-100
    private double frequencyAnomalyScore;  // ← THÊM: 0-100
    private double recipientAnomalyScore;  // ← THÊM: 0-100 (sửa recipientDiversity)
}