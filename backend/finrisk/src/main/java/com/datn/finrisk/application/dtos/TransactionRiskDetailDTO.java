 

package com.datn.finrisk.application.dtos;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class TransactionRiskDetailDTO {
    private Long id;
    private String locationIp;
    private String deviceFingerprint;
    private Integer totalRiskScore;
    private Integer behaviorScore;
    private Integer ruleScore;
    private List<String> violatedRules;
    private List<AiInsightDTO> aiInsights;
    private Map<String, CategoryBreakdownDTO> categoryBreakdown;
    private int aiContribution;

    private ProfileBaselineDTO profileBaseline;

 
    @Data
    public static class CategoryBreakdownDTO {
        private int raw;
        private int cap;
        private int effective;
        private int ruleCount;
    }

    @Data
    public static class AiInsightDTO {
        private String feature;
        private String message;
        private String type;
        private Double anomalyScore;
    }

 
    @Data
    public static class ProfileBaselineDTO {

 
        private String avgAmount;      
        private String stdAmount;        
        private String avgHour;       
        private String avgGapHours;      
        private String noveltyRate;     
        private Integer txCount;         
        private String phase;           
 
        private Double amountCV;        
        private Double circularVariance; 
        private Double frequencyCV;     
        private Double noveltyRateRaw;   
                                        
    }
}