package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserSecurity userSecurity;

    private String fullName;

    @Column(unique = true, nullable = false)
    private String phoneNumber;

    @Column(unique = true)
    private String email;

    private String status = "ACTIVE";

    /**
     * Multi-angle InsightFace embeddings — JSON array of 512-dim vectors.
     * Ví dụ: "[[0.02, -0.18, ...], [0.05, 0.11, ...], ...]"
     * Tạo ra bởi Python /api/ai/enroll-face-batch (front, left, right, up, down).
     * Không bao giờ expose ra ngoài API.
     */
    @JsonIgnore
    @Column(name = "face_embeddings", columnDefinition = "LONGTEXT")
    private String faceEmbeddings;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(length = 500)
    private String currentRefreshToken;

    @Column(length = 45)
    private String lastLoginIp;

    @Column(length = 255)
    private String lastLoginDevice;

    private boolean isSuspiciousSession = false;

    @Column(name = "admin_flagged")
    private boolean adminFlagged = false;

    // P2.1: Nhân khẩu học — dùng cho Peer-Group cold-start profiling
    @Column(name = "age_range", length = 10)
    private String ageRange;

    @Column(name = "region", length = 100)
    private String region;

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserBehaviorProfile behaviorProfile;

    // ── Helper methods ────────────────────────────────────────────────────────

    public boolean hasFaceEmbeddings() {
        return faceEmbeddings != null && !faceEmbeddings.isBlank();
    }
}