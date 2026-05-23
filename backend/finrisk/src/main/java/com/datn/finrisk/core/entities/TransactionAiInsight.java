package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_ai_insights")
@Data
@NoArgsConstructor
public class TransactionAiInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
 
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

 
    @Column(name = "feature_name", length = 50)
    private String featureName;
 
    @Column(name = "insight_message", columnDefinition = "TEXT")
    private String insightMessage;

    
    @Column(name = "insight_type", length = 20)
    private String insightType;

 
    @Column(name = "anomaly_score")
    private Double anomalyScore;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    public TransactionAiInsight(Transaction tx, String feature, String message, String type, Double score) {
        this.transaction = tx;
        this.featureName = feature;
        this.insightMessage = message;
        this.insightType = type;
        this.anomalyScore = score;
    }
}