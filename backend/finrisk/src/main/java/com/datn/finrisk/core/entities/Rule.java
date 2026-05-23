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
    private String conditions; 

 
    @Column(name = "spel_expression", columnDefinition = "TEXT")
    private String spelExpression;

    private Integer actionScore;

    private Boolean isActive = true;

 
    @Column(name = "min_policy_override", length = 20)
    private String minPolicyOverride;
 
}