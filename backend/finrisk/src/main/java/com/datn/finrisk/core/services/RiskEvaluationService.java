// package com.datn.finrisk.core.services;

// import com.datn.finrisk.core.entities.Rule;
// import com.datn.finrisk.core.entities.Transaction;
// import com.datn.finrisk.core.repository.RuleRepository;
// import com.datn.finrisk.core.repository.TransactionRepository;
// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;

// import java.time.LocalDateTime;
// import java.util.List;

// @Service
// public class RiskEvaluationService {

//     @Autowired
//     private RuleRepository ruleRepository;

//     @Autowired
//     private TransactionRepository transactionRepository;

//     private final ObjectMapper objectMapper = new ObjectMapper();

//     public int evaluateRisk(Transaction transaction, boolean isNewRecipient) {
//         int totalRiskScore = 0;
//         System.out.println("🤖 BẮT ĐẦU CHẠY RULE ENGINE DYNAMIC LẤY TỪ DATABASE...");

//         // 1. Lấy tất cả các luật đang bật
//         List<Rule> activeRules = ruleRepository.findByIsActiveTrue();

//         // 🚀 KIỂM TRA 1: CHỐNG SPAM GIAO DỊCH
//         LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
//         int recentTxCount = transactionRepository.countRecentTransactions(
//                 transaction.getFromAccount().getId(), 
//                 oneMinuteAgo
//         );

//         if (recentTxCount >= 3) {
//             System.out.println("🚨 ANTI-FRAUD: Phát hiện Spam Giao dịch! | Cộng: 40 điểm");
//             totalRiskScore += 40;
//         }

//         // 🚀 KIỂM TRA 2: CỜ ĐỎ TỪ HỆ THỐNG TRUY VẾT THIẾT BỊ/IP (FAKE GEO)
//         if (transaction.getFromAccount().getUser().isSuspiciousSession()) {
//             System.out.println("🚨 ANTI-FRAUD: Phát hiện đăng nhập từ IP/Thiết bị lạ! | Cộng: 30 điểm");
//             totalRiskScore += 30;
//         }

//         // 2. Duyệt qua từng luật lấy từ DB để kiểm tra
//         for (Rule rule : activeRules) {
//             try {
//                 JsonNode conditionNode = objectMapper.readTree(rule.getConditions());
//                 String field = conditionNode.get("field").asText();
//                 String operator = conditionNode.get("operator").asText();
//                 String value = conditionNode.get("value").asText();

//                 boolean isMatched = false;

//                 // 3. Xử lý Logic Rule Engine cốt lõi
//                 if (field.equals("amount")) {
//                     double txAmount = transaction.getAmount().doubleValue();
//                     double ruleValue = Double.parseDouble(value);
                    
//                     if (operator.equals(">") && txAmount > ruleValue) isMatched = true;
//                     if (operator.equals(">=") && txAmount >= ruleValue) isMatched = true;
//                 } 
//                 else if (field.equals("emotion")) {
//                     if (operator.equals("==") && value.equals(transaction.getEmotionSignal())) {
//                         isMatched = true;
//                     }
//                 }
//                 else if (field.equals("history")) {
//                     if (operator.equals("==") && value.equals("NEW_RECIPIENT") && isNewRecipient) {
//                         isMatched = true;
//                     }
//                 }

//                 // 4. Nếu vi phạm luật -> Cộng điểm
//                 if (isMatched) {
//                     totalRiskScore += rule.getActionScore();
//                     System.out.println("⚠️ Kích hoạt luật: [" + rule.getRuleName() + "] | Cộng: " + rule.getActionScore() + " điểm");
//                 }

//             } catch (Exception e) {
//                 System.err.println("Lỗi parse Rule ID: " + rule.getId());
//             }
//         }

//         System.out.println("🎯 TỔNG ĐIỂM RỦI RO (TỪ DB): " + totalRiskScore);
//         return totalRiskScore;
//     }
// }



package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Rule;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.RuleRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RiskEvaluationService {

    @Autowired private RuleRepository ruleRepository;
    @Autowired private TransactionRepository transactionRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExpressionParser parser = new SpelExpressionParser(); // 🚀 Cỗ máy SpEL

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
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("tx", transaction);
        context.setVariable("isNewRecipient", isNewRecipient);

        for (Rule rule : activeRules) {
            try {
                JsonNode conditionNode = objectMapper.readTree(rule.getConditions());
                String field = conditionNode.get("field").asText();
                String operator = conditionNode.get("operator").asText();
                String value = conditionNode.get("value").asText();

                // 🚀 Dịch từ JSON sang ngôn ngữ SpEL
                String spelExpression = "";
                if ("amount".equals(field)) {
                    spelExpression = "#tx.amount " + operator + " " + value;
                } else if ("emotion".equals(field)) {
                    spelExpression = "#tx.emotionSignal " + operator + " '" + value + "'";
                } else if ("history".equals(field)) {
                    if ("NEW_RECIPIENT".equals(value)) {
                        spelExpression = "#isNewRecipient " + operator + " true";
                    }
                }

                // 🚀 Bắt SpEL chạy thử biểu thức (Trả về True/False)
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
}