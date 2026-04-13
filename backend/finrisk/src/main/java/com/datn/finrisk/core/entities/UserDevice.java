package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_devices")
@Data
public class UserDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String deviceFingerprint;

    private String deviceName;

    private Boolean isTrusted = false;

    private String lastUsedIp;

    private LocalDateTime lastUsedAt = LocalDateTime.now();
    private LocalDateTime createdAt = LocalDateTime.now();
}