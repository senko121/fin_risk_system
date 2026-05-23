 

package com.datn.finrisk.application.dtos;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminTransactionDTO {
    private Long id;
 
    private String senderAccountNumber; 
    private String senderFullName;      
    private String senderUsername;      
    private boolean senderSuspicious;   

 
    private String toAccountNumber;
    private String recipientFullName;
    private String toBankCode;          
 
    private BigDecimal amount;
    private String status;
    private String riskLevel;
    private Integer totalRiskScore;
    private String emotionSignal;
    private LocalDateTime createdAt;

 
}