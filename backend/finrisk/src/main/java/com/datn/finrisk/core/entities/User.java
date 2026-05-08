package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore; 
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 🚀 Bổ sung import

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // 🚀 DÁN BÙA TRỊ LỖI PROXY
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

    @Column(columnDefinition = "LONGTEXT")
    private String base64FaceImage;

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
}