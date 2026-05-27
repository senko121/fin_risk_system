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
     * @deprecated Dùng faceEmbedding thay thế.
     * Giữ lại trong thời gian migration, sẽ xóa sau khi toàn bộ
     * user đã được re-enroll hoặc migrate embedding.
     */
    @Deprecated
    @Column(columnDefinition = "LONGTEXT")
    private String base64FaceImage;

    /**
     * ArcFace embedding vector (512 floats) được serialize thành JSON array.
     * Ví dụ: "[0.0234, -0.1823, 0.4521, ...]"
     * Được tạo ra bởi Python /api/ai/enroll-face và lưu tại đây.
     * Không bao giờ expose ra ngoài API.
     */
    @JsonIgnore
    @Column(name = "face_embedding", columnDefinition = "LONGTEXT")
    private String faceEmbedding;

    /**
     * Multi-angle InsightFace embeddings — JSON array of 512-dim vectors.
     * Ví dụ: "[[0.02, -0.18, ...], [0.05, 0.11, ...], ...]"
     * Tạo ra bởi Python /api/ai/enroll-face-batch (front, left, right, up, down).
     * Ưu tiên hơn faceEmbedding khi verify vì min-distance qua nhiều góc.
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

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserBehaviorProfile behaviorProfile;

    // ── Helper methods ────────────────────────────────────────────────────────

    /** Multi-angle embeddings (InsightFace buffalo_l) — đường verify tốt nhất. */
    public boolean hasFaceEmbeddings() {
        return faceEmbeddings != null && !faceEmbeddings.isBlank();
    }

    /** Single embedding (ArcFace legacy, một góc mặt). */
    public boolean hasFaceEmbedding() {
        return faceEmbedding != null && !faceEmbedding.isBlank();
    }

    /** Kiểm tra user vẫn còn dùng ảnh cũ (chưa migrate). */
    public boolean hasLegacyFaceImage() {
        return base64FaceImage != null && !base64FaceImage.isBlank();
    }
}