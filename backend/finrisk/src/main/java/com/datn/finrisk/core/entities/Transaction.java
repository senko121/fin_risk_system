package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    private String toAccountNumber;
    private String toBankCode;
    private BigDecimal amount;
    private String deviceFingerprint;
    
    private String emotionSignal; // Tín hiệu cảm xúc thu được [cite: 121-122]
    private Integer totalRiskScore = 0;
    private String riskLevel; // LOW, MEDIUM, HIGH [cite: 113-115]
    
    private String status = "PENDING";
    private LocalDateTime createdAt = LocalDateTime.now();
}