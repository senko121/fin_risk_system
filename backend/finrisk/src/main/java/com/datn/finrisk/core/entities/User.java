package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    private String fullName;

    @Column(unique = true, nullable = false)
    private String phoneNumber;

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

    @Column(length = 45) // IPv6 có thể dài đến 45 ký tự
    private String lastLoginIp;

    @Column(length = 255)
    private String lastLoginDevice;

    // Trường này cực quan trọng: Cờ hiệu rủi ro để Rule Engine đọc
    private boolean isSuspiciousSession = false;
}