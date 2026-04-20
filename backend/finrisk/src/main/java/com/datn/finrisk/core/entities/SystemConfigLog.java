package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_config_logs")
@Data
@NoArgsConstructor
public class SystemConfigLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String adminUsername;
    private String actionType; // UPDATE_RULE, CREATE_POLICY...
    private String targetTable; // rules, risk_policies
    private Long targetId;

    @Column(columnDefinition = "JSON")
    private String oldValue;

    @Column(columnDefinition = "JSON")
    private String newValue;

    private LocalDateTime createdAt = LocalDateTime.now();

    public SystemConfigLog(String adminUsername, String actionType, String targetTable, Long targetId, String oldValue, String newValue) {
        this.adminUsername = adminUsername;
        this.actionType = actionType;
        this.targetTable = targetTable;
        this.targetId = targetId;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
}