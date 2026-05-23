package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;      
import java.util.ArrayList;      

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; 

@Entity
@Table(name = "transactions")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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
    
    private String emotionSignal; 
    private Integer totalRiskScore = 0;
    private String riskLevel;  

    private String locationIp;  
    
    private String status = "PENDING";
    private LocalDateTime createdAt = LocalDateTime.now();

    private Integer failedAiAttempts = 0;

 
    @JsonIgnore  
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RiskScore> riskScores = new ArrayList<>();
 
    @JsonIgnore
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TransactionAiInsight> aiInsights = new ArrayList<>();

    @Transient   
    private String policyOverride;
}