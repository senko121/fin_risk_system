package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * P2.1 — Thống kê hành vi trung bình của một nhóm nhân khẩu học.
 * Dùng làm prior Mahalanobis khi user có < 10 giao dịch (cold-start).
 */
@Entity
@Table(name = "peer_groups",
       uniqueConstraints = @UniqueConstraint(name = "uk_peer_group",
                                              columnNames = {"age_range", "region"}))
@Data
@NoArgsConstructor
public class PeerGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "age_range", length = 10, nullable = false)
    private String ageRange;

    @Column(name = "region", length = 100, nullable = false)
    private String region;

    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mean_vector", columnDefinition = "json", nullable = false)
    private List<Double> meanVector;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "covariance_matrix", columnDefinition = "json", nullable = false)
    private List<List<Double>> covarianceMatrix;

    @Column(name = "last_updated_at")
    private LocalDateTime lastUpdatedAt = LocalDateTime.now();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        lastUpdatedAt = LocalDateTime.now();
    }
}
