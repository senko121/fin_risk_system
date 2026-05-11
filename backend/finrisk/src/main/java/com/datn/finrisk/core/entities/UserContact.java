package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString; // 🚀 Bổ sung import
import lombok.EqualsAndHashCode; // 🚀 Bổ sung import
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore; // 🚀 Bổ sung import

@Entity
@Table(name = "user_contacts")
@Data
public class UserContact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🚀 DÁN 3 CÁI BÙA VÀO ĐÂY
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User owner;

    private String contactAccountNumber;
    private String contactName;
    private boolean isPinned = false;
    private LocalDateTime createdAt = LocalDateTime.now();
}