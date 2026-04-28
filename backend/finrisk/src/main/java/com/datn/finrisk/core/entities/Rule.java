package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rules")
@Data
public class Rule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ruleName;
    
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "JSON")
    private String conditions; // Lưu logic JSON: {"field": "amount", "operator": ">", "value": 50000000}

    // 🚀 BỔ SUNG TRƯỜNG NÀY ĐỂ MAP VỚI CỘT MỚI TRONG DATABASE
    @Column(name = "spel_expression", columnDefinition = "TEXT")
    private String spelExpression;

    private Integer actionScore;

    private Boolean isActive = true;
}