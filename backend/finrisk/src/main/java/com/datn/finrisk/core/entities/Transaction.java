package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 🚀 Bổ sung import

@Entity
@Table(name = "transactions")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // 🚀 DÁN BÙA TRỊ LỖI PROXY
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    private String toAccountNumber;
    private String toBankCode;
    private BigDecimal amount;
    private String description;
    private String deviceFingerprint;
    
    private String emotionSignal; // Tín hiệu cảm xúc thu được
    private Integer totalRiskScore = 0;
    private String riskLevel; // LOW, MEDIUM, HIGH

    private String locationIp; // Lưu IP lúc thực hiện giao dịch
    
    private String status = "PENDING";
    private LocalDateTime createdAt = LocalDateTime.now();
} // 🚀 Đã thêm ngoặc nhọn đóng class bị thiếu