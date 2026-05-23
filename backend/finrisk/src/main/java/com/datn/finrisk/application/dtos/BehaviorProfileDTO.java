package com.datn.finrisk.application.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BehaviorProfileDTO {
 
    private Long userId;
    private int txCount;
    private String profilingPhase;
    private double profileReliability;
    private String calculationMethod;
    private String lastUpdated;
 
    private double avgAmount;
    private String avgTransactionHour;
    private double avgGapHours;

 
    private double stdAmount;          
    private double stdGapHours;         

 
    private double anomalyScore;
    private String anomalyLevel;
 
    private double amountAnomalyScore;     
    private double hourAnomalyScore;       
    private double frequencyAnomalyScore;  
    private double recipientAnomalyScore;   
}