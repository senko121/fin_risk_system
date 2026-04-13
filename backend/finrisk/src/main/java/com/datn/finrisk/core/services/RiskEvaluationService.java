package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Rule;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.RuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RiskEvaluationService {

    @Autowired
    private RuleRepository ruleRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Hàm này nhận vào 1 giao dịch và trả về TỔNG ĐIỂM RỦI RO [cite: 98-103]
    public int evaluateRisk(Transaction transaction) {
        int totalRiskScore = 0;

        // 1. Lấy tất cả các luật đang bật (is_active = true) [cite: 100]
        List<Rule> activeRules = ruleRepository.findByIsActiveTrue();

        // 2. Duyệt qua từng luật để kiểm tra [cite: 101]
        for (Rule rule : activeRules) {
            try {
                // Đọc cột conditions (JSON) dưới DB
                // Ví dụ: {"field": "amount", "operator": ">", "value": 50000000}
                JsonNode conditionNode = objectMapper.readTree(rule.getConditions());
                String field = conditionNode.get("field").asText();
                String operator = conditionNode.get("operator").asText();
                String value = conditionNode.get("value").asText();

                boolean isMatched = false;

                // 3. Xử lý Logic Rule Engine cốt lõi
                if (field.equals("amount")) {
                    double txAmount = transaction.getAmount().doubleValue();
                    double ruleValue = Double.parseDouble(value);
                    
                    if (operator.equals(">") && txAmount > ruleValue) isMatched = true;
                    if (operator.equals(">=") && txAmount >= ruleValue) isMatched = true;
                    // Bro có thể thêm <, <=, == tùy ý
                } 
                else if (field.equals("emotion")) {
                    // Logic cộng điểm nếu cảm xúc là STRESS [cite: 97]
                    if (operator.equals("==") && value.equals(transaction.getEmotionSignal())) {
                        isMatched = true;
                    }
                }
                // (Bro có thể mở rộng thêm check device, location ở đây sau)

                // 4. Nếu vi phạm luật -> Cộng điểm [cite: 102]
                if (isMatched) {
                    totalRiskScore += rule.getActionScore();
                    System.out.println("⚠️ Vi phạm luật: " + rule.getRuleName() + " | Cộng: " + rule.getActionScore() + " điểm");
                    // Tương lai: Mình sẽ Save thông tin vi phạm này vào bảng risk_scores ở đây
                }

            } catch (Exception e) {
                System.err.println("Lỗi parse Rule ID: " + rule.getId());
            }
        }

        return totalRiskScore;
    }
}