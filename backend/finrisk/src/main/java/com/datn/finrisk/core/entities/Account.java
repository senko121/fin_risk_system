package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString; 
import lombok.EqualsAndHashCode; 
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore; 
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 🚀 Bổ sung import

@Entity
@Table(name = "accounts")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // 🚀 DÁN BÙA TRỊ LỖI PROXY
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🚀 DÁN 3 CÁI BÙA VÀO ĐÂY
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
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