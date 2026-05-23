 
package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Rule;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.TransactionAiInsight;
import com.datn.finrisk.core.repository.RuleRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.datn.finrisk.application.dtos.EmotionAIResponse;
import com.datn.finrisk.application.dtos.FaceAIResponse;
import com.datn.finrisk.application.dtos.BehaviorInsightResult;
import com.datn.finrisk.core.repository.RiskScoreRepository;
import com.datn.finrisk.core.entities.RiskScore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

// Transaction B6: đây là não tính điểm chốt lại điểm rủi ro -> Transaction B7: RiskPolicyRepository
@Service
public class RiskEvaluationService {

    @Autowired private RestTemplate restTemplate;
    @Autowired private RuleRepository ruleRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private RiskScoreRepository riskScoreRepository;
    @Autowired private com.datn.finrisk.core.repository.UserBehaviorProfileRepository profileRepository;
    @Autowired private com.datn.finrisk.core.services.BehavioralProfilingService behavioralProfilingService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExpressionParser parser = new SpelExpressionParser();  
 
    private static final double MAX_RULE_CAP    = 150.0;  
    private static final double AI_WEIGHT_MIN   = 0.10;   
    private static final double AI_WEIGHT_MAX   = 0.50;  
    private static final int    AI_MATURE_COUNT = 150;    
    private static final int    AI_ACTIVE_COUNT = 50;     
    private static final int    VETO_AI_THRESHOLD   = 85; 
    private static final int    VETO_RULE_THRESHOLD = 85;  
    private static final int    VETO_MIN_SCORE      = 75; 
    private static final int    VETO_RULE_MIN_SCORE = 80;  

 
    private static final Map<String, Integer> OVERRIDE_PRIORITY = Map.of(
        "MEDIUM_1", 1,
        "MEDIUM_2", 2,
        "HIGH",     3
    );

 
    public int evaluateRisk(Transaction transaction, boolean isNewRecipient, List<RiskScore> pendingRiskLogs, List<TransactionAiInsight> pendingAiInsights) {
        System.out.println("🤖 BẮT ĐẦU CHẠY RULE ENGINE + BEHAVIORAL PROFILING...");

        User sender = transaction.getFromAccount().getUser();
        List<Rule> activeRules = ruleRepository.findByIsActiveTrue();
 
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        int recentTxCount = transactionRepository.countRecentTransactions(transaction.getFromAccount().getId(), oneMinuteAgo);
        
        double balanceRatio = 0.0;
        double currentBalance = transaction.getFromAccount().getBalance().doubleValue();
        if (currentBalance > 0) {
            balanceRatio = transaction.getAmount().doubleValue() / currentBalance;
        }

        int currentHour = LocalDateTime.now().getHour();
        boolean isNightTime = (currentHour >= 23 || currentHour < 5);

        LocalDateTime startOfDay = java.time.LocalDate.now().atStartOfDay();
        BigDecimal sumToday = transactionRepository.sumSuccessfulAmountToday(transaction.getFromAccount().getId(), startOfDay);
        double totalTransferredToday = (sumToday != null) ? sumToday.doubleValue() : 0.0;
        double dailyTotalAmount = totalTransferredToday + transaction.getAmount().doubleValue();
 
        com.datn.finrisk.core.entities.UserBehaviorProfile profile = profileRepository.findByUserId(sender.getId()).orElse(null);
        
        double gapSeconds = 86400.0; 
        if (profile != null && profile.getLastTxTimestamp() != null) {
            gapSeconds = java.time.Duration.between(profile.getLastTxTimestamp(), LocalDateTime.now()).getSeconds();
        }
        
        double recipientNovelty = isNewRecipient ? 1.0 : 0.0;

        BehaviorInsightResult behaviorResult = behavioralProfilingService
            .calculateBehavioralAnomalyScore(transaction, profile, gapSeconds, recipientNovelty);

        int behavioralScore = behaviorResult.getTotalScore();
        pendingAiInsights.addAll(behaviorResult.getInsights()); 
        
        int txCount = (profile != null) ? profile.getTxCount() : 0;
        System.out.printf("🧠 ĐIỂM THÓI QUEN (BEHAVIOR): %d | txCount: %d%n", behavioralScore, txCount);

 
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("tx", transaction);
        context.setVariable("isNewRecipient", isNewRecipient);
        boolean combinedSuspiciousRisk = sender.isSuspiciousSession() || sender.isAdminFlagged();
        context.setVariable("suspiciousSession", combinedSuspiciousRisk);
        context.setVariable("deviceTrusted", true);
        context.setVariable("recentTxCount", recentTxCount);
        context.setVariable("balanceRatio", balanceRatio);
        context.setVariable("isNightTime", isNightTime);
        context.setVariable("dailyTotalAmount", dailyTotalAmount);

 
        int rulePositive = 0;
        int ruleNegative = 0;
        String activeOverride = null; 

        for (Rule rule : activeRules) {
            try {
                String spelExpression = rule.getSpelExpression();
                if (spelExpression != null && !spelExpression.isEmpty()) {
                    Boolean isMatched = parser.parseExpression(spelExpression).getValue(context, Boolean.class);
                    
                    if (Boolean.TRUE.equals(isMatched)) {
                        int score = rule.getActionScore();
                        System.out.printf("  ✓ Khớp luật: [%s] -> Điểm: %+d%n", rule.getRuleName(), score);

                        if (score > 0) rulePositive += score;
                        else           ruleNegative += Math.abs(score);

 
                        String override = rule.getMinPolicyOverride();
                        if (override != null && !override.isBlank()) {
                            if (activeOverride == null || 
                                OVERRIDE_PRIORITY.getOrDefault(override, 0) > OVERRIDE_PRIORITY.getOrDefault(activeOverride, 0)) {
                                activeOverride = override;
                                System.out.println("  📌 Override kích hoạt: " + override + " từ luật [" + rule.getRuleName() + "]");
                            }
                        }

                        if (score != 0) {
                            RiskScore riskLog = new RiskScore();
                            riskLog.setRule(rule);
                            riskLog.setAppliedScore(score);
                            pendingRiskLogs.add(riskLog); 
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠ Lỗi SpEL tại Rule [" + rule.getRuleName() + "]: " + e.getMessage());
            }
        }
        
        System.out.printf("⚖ Raw Rule Score: (+)%d | (-)%d%n", rulePositive, ruleNegative);

 
 
        int rawRuleScore = Math.max(0, rulePositive - ruleNegative);
        int normalizedRuleScore = (int) Math.min((rawRuleScore / MAX_RULE_CAP) * 100.0, 100.0);

 
        double aiReliability;
        if (txCount < AI_ACTIVE_COUNT) {
            aiReliability = 0.0; 
        } else if (txCount >= AI_MATURE_COUNT) {
            aiReliability = 1.0;
        } else {
            aiReliability = (double)(txCount - AI_ACTIVE_COUNT) / (AI_MATURE_COUNT - AI_ACTIVE_COUNT);
        }
 
        double aiWeight   = AI_WEIGHT_MIN + (AI_WEIGHT_MAX - AI_WEIGHT_MIN) * aiReliability;
        double ruleWeight = 1.0 - aiWeight;

        System.out.printf("📊 Trọng số → AI: %.0f%% (reliability=%.2f) | Rule: %.0f%%%n", aiWeight * 100, aiReliability, ruleWeight * 100);
        System.out.printf("📊 Điểm chuẩn hóa → Behavior: %d | Rule: %d%n", behavioralScore, normalizedRuleScore);

 
        double blendedScore = (behavioralScore * aiWeight) + (normalizedRuleScore * ruleWeight);
        int finalRiskScore  = (int) Math.round(blendedScore);

 
        boolean aiVetoTriggered   = behavioralScore >= VETO_AI_THRESHOLD && txCount >= AI_ACTIVE_COUNT; 
        boolean ruleVetoTriggered = normalizedRuleScore >= VETO_RULE_THRESHOLD;

        if (aiVetoTriggered) {
            System.out.println("🚨 AI VETO: Behavior=" + behavioralScore + " >= " + VETO_AI_THRESHOLD + " → Điểm tối thiểu " + VETO_MIN_SCORE);
            finalRiskScore = Math.max(finalRiskScore, VETO_MIN_SCORE);
        }
        if (ruleVetoTriggered) {
            System.out.println("🚨 RULE VETO: Rule=" + normalizedRuleScore + " >= " + VETO_RULE_THRESHOLD + " → Điểm tối thiểu " + VETO_RULE_MIN_SCORE);
            finalRiskScore = Math.max(finalRiskScore, VETO_RULE_MIN_SCORE);
        }
 
        finalRiskScore = Math.max(0, Math.min(finalRiskScore, 100));

        
        if (activeOverride != null) {
            transaction.setPolicyOverride(activeOverride);
        }

        System.out.printf("🎯 TỔNG ĐIỂM CUỐI: %d | Override: %s%n", finalRiskScore, activeOverride != null ? activeOverride : "Không có");
        return finalRiskScore;
    }

 
    @Async("aiTaskExecutor")
    public CompletableFuture<FaceAIResponse> verifyIdentityAsync(String liveBase64, String regBase64) {
        System.out.println("--- [STEP 1: FACE-ID] Đang gửi ảnh sang Port 5000... ---");
        try {
            String url = "http://localhost:5000/api/ai/verify-face";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("live_image_base64", liveBase64);
            requestMap.put("registered_image_base64", regBase64);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestMap, headers);
            FaceAIResponse response = restTemplate.postForObject(url, entity, FaceAIResponse.class);
            
            if (response != null) {
                System.out.println("✅ [STEP 1: FACE-ID] Phản hồi: Matched=" + response.isMatched());
            }
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            System.err.println("❌ [STEP 1: FACE-ID] LỖI: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<EmotionAIResponse> detectEmotionAsync(String liveBase64) {
        System.out.println("--- [STEP 2: EMOTION] Đang gửi ảnh sang Port 5001... ---");
        try {
            String url = "http://localhost:5001/api/ai/detect-emotion";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("image_base64", liveBase64);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestMap, headers);
            EmotionAIResponse response = restTemplate.postForObject(url, entity, EmotionAIResponse.class);
            
            if(response != null) {
                 System.out.println("✅ [STEP 2: EMOTION] Cảm xúc: " + response.getEmotion());
            }
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            System.err.println("❌ [STEP 2: EMOTION] LỖI: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<String> verifyVoiceLivenessBase64Async(String audioBase64) {
        System.out.println("--- [STEP VOICE-AI] Đang giải mã Base64 và gửi Audio sang Port 5003... ---");
        try {
            String url = "http://localhost:5003/api/ai/verify-voice";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            byte[] decodedAudio = java.util.Base64.getDecoder().decode(audioBase64);
            org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(decodedAudio) {
                @Override
                public String getFilename() {
                    return "websocket_voice.wav"; 
                }
            };
            body.add("audio_file", fileResource);

            HttpEntity<org.springframework.util.MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            JsonNode response = restTemplate.postForObject(url, requestEntity, JsonNode.class);
            
            if (response != null && response.has("authCode")) {
                String authCode = response.get("authCode").asText();
                System.out.println("✅ [VOICE-AI] Python nhận diện thành công mã: " + authCode);
                return CompletableFuture.completedFuture(authCode);
            }
            return CompletableFuture.completedFuture("");
        } catch (Exception e) {
            System.err.println("❌ [VOICE-AI] LỖI GIAO TIẾP VỚI PYTHON (Port 5003): " + e.getMessage());
            return CompletableFuture.completedFuture("");
        }
    }
}