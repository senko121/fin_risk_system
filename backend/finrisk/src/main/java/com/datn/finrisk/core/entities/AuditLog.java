package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

 
    @Column(nullable = false)
    private String username;
 
    @Column(nullable = false)
    private String action;

 
    @Column(columnDefinition = "TEXT")
    private String details;

 
    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
    
    public AuditLog(String username, String action, String details) {
        this.username = username;
        this.action = action;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }
}