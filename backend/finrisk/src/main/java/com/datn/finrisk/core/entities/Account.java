package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString; // 🚀 Bổ sung import
import lombok.EqualsAndHashCode; // 🚀 Bổ sung import
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore; // 🚀 Bổ sung import

@Entity
@Table(name = "accounts")
@Data
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🚀 DÁN 3 CÁI BÙA VÀO ĐÂY
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(unique = true)
    private String accountNumber;

    private BigDecimal balance;

    private String currency = "VND";

    private String status = "ACTIVE";
    
    @Version
    private Integer version;
}