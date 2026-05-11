
package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString; 
import lombok.EqualsAndHashCode;  
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;  

@Entity
@Table(name = "user_securities")
@Data
@NoArgsConstructor
public class UserSecurity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

 
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private String pinHash;

    @Column(nullable = false)
    private String passwordHash;

    private Boolean isPinSetup = false;

    private Boolean twoFactorEnabled = false;

    private Integer failedLoginAttempts = 0;

    private Integer failedPinAttempts = 0;

    private LocalDateTime lockUntil;

    private LocalDateTime lastPasswordChange;

    private LocalDateTime lastPinChange;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}