package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Rule;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.RuleRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.datn.finrisk.application.dtos.FaceAIResponse;

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

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.HashMap;



import java.time.LocalDateTime;
import java.util.List;

@Service
public class RiskEvaluationService {

    @Autowired private RestTemplate restTemplate;
    @Autowired private RuleRepository ruleRepository;
    @Autowired private TransactionRepository transactionRepository;

    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExpressionParser parser = new SpelExpressionParser(); // Cỗ máy SpEL

    public int evaluateRisk(Transaction transaction, boolean isNewRecipient) {
        int totalRiskScore = 0;
        System.out.println("🤖 BẮT ĐẦU CHẠY RULE ENGINE DYNAMIC (SỬ DỤNG SpEL)...");

        List<Rule> activeRules = ruleRepository.findByIsActiveTrue();

        // Check Spam & IP (Giữ nguyên logic bảo mật cốt lõi)
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        int recentTxCount = transactionRepository.countRecentTransactions(transaction.getFromAccount().getId(), oneMinuteAgo);
        if (recentTxCount >= 3) {
            System.out.println("🚨 ANTI-FRAUD: Phát hiện Spam! | Cộng: 40 điểm");
            totalRiskScore += 40;
        }

        // Bơm bối cảnh (Data) vào cho SpEL đọc
        // Bơm bối cảnh (Data) vào cho SpEL đọc
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("tx", transaction);
        context.setVariable("isNewRecipient", isNewRecipient);
        
        // 🚀 BƠM THÊM 2 BIẾN MỚI TỪ ENTITY USER
        boolean isSuspicious = transaction.getFromAccount().getUser().isSuspiciousSession();
        context.setVariable("suspiciousSession", isSuspicious);
        
        // Tạm thời hardcode deviceTrusted = true để test (Sau này bro query từ bảng UserDevice nhé)
        context.setVariable("deviceTrusted", true); 

        for (Rule rule : activeRules) {
            try {
                JsonNode conditionNode = objectMapper.readTree(rule.getConditions());
                String field = conditionNode.get("field").asText();
                String operator = conditionNode.get("operator").asText();
                String value = conditionNode.get("value").asText();

                String spelExpression = "";
                
                // 🚀 DẠY SpEL CÁCH ĐỌC 4 LOẠI FIELD CHÚNG TA ĐANG CÓ
                if ("amount".equals(field)) {
                    spelExpression = "#tx.amount " + operator + " " + value;
                } else if ("history".equals(field)) {
                    if ("NEW_RECIPIENT".equals(value)) {
                        spelExpression = "#isNewRecipient " + operator + " true";
                    }
                } else if ("suspiciousSession".equals(field)) {
                    spelExpression = "#suspiciousSession " + operator + " " + value;
                } else if ("deviceTrusted".equals(field)) {
                    spelExpression = "#deviceTrusted " + operator + " " + value;
                }

                // Bắt SpEL chạy thử biểu thức (Trả về True/False)
                if (!spelExpression.isEmpty()) {
                    Boolean isMatched = parser.parseExpression(spelExpression).getValue(context, Boolean.class);
                    if (Boolean.TRUE.equals(isMatched)) {
                        totalRiskScore += rule.getActionScore();
                        System.out.println("⚠️ Khớp luật: [" + rule.getRuleName() + "] -> Điểm: +" + rule.getActionScore());
                    }
                }
            } catch (Exception e) {
                System.err.println("Lỗi SpEL tại Rule ID " + rule.getId() + ": " + e.getMessage());
            }
        }

        System.out.println("🎯 TỔNG ĐIỂM RỦI RO LÀ: " + totalRiskScore);
        return totalRiskScore;
    }

// 1. Fix gọi sang FaceID (Port 5000)
    @Async("aiTaskExecutor")
    public CompletableFuture<FaceAIResponse> verifyIdentityAsync(String liveBase64, String regBase64) {
        System.out.println("--- [STEP 1: FACE-ID] Đang gửi ảnh sang Port 5000... ---");
        try {
            String url = "http://localhost:5000/api/ai/verify-face";
            
            // 🔥 BẮT BUỘC: Tạo Header JSON
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("live_image_base64", liveBase64);
            requestMap.put("registered_image_base64", regBase64);

            // Gói vào HttpEntity
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

    // 2. Fix gọi sang Emotion (Port 5001)
    @Async("aiTaskExecutor")
    public CompletableFuture<String> detectEmotionAsync(String liveBase64) {
        System.out.println("--- [STEP 2: EMOTION] Đang gửi ảnh sang Port 5001... ---");
        try {
            String url = "http://localhost:5001/api/ai/detect-emotion";
            
            // 🔥 BẮT BUỘC: Tạo Header JSON
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("image_base64", liveBase64);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestMap, headers);

            // Nhận kết quả trực tiếp bằng JsonNode cho chính xác
            JsonNode response = restTemplate.postForObject(url, entity, JsonNode.class);
            String emotion = response.get("emotion").asText();
            
            System.out.println("✅ [STEP 2: EMOTION] Cảm xúc: " + emotion);
            return CompletableFuture.completedFuture(emotion);
        } catch (Exception e) {
            System.err.println("❌ [STEP 2: EMOTION] LỖI: " + e.getMessage());
            return CompletableFuture.completedFuture("UNKNOWN");
        }
    }
}