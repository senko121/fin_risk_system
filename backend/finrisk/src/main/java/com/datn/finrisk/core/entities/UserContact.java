package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_contacts")
@Data
public class UserContact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User owner; // Chủ danh bạ

    private String contactAccountNumber;
    private String contactName;
    private boolean isPinned = false;
    private LocalDateTime createdAt = LocalDateTime.now();
}