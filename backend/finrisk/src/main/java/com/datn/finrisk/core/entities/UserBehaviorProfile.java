package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "user_behavior_profiles")
@Data
@NoArgsConstructor
public class UserBehaviorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "tx_count")
    private Integer txCount = 0;

     
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mean_vector", columnDefinition = "json")
    private List<Double> meanVector;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "covariance_matrix_c", columnDefinition = "json")
    private List<List<Double>> covarianceMatrixC;  

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ewma_mean_vector", columnDefinition = "json")
    private List<Double> ewmaMeanVector;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ewma_variance", columnDefinition = "json")
    private List<Double> ewmaVariance;

    @Column(name = "last_tx_timestamp")
    private LocalDateTime lastTxTimestamp;

    @Version
    private Integer version;  

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}