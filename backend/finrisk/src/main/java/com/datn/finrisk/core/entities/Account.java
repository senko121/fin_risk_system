package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
@Data
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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