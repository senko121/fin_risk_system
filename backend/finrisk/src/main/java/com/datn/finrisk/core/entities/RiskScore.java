package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "risk_scores")
@Data
public class RiskScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore          // ✅ Cắt circular reference tại đây
    @ToString.Exclude    // ✅ Tránh Lombok toString loop
    @EqualsAndHashCode.Exclude
    @ManyToOne
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne
    @JoinColumn(name = "rule_id", nullable = false)
    private Rule rule;

    @Column(nullable = false)
    private Integer appliedScore;

    private LocalDateTime createdAt = LocalDateTime.now();
}