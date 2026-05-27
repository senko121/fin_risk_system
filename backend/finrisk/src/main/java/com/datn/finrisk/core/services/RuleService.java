package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Rule;
import com.datn.finrisk.core.entities.SystemConfigLog;
import com.datn.finrisk.core.repository.RuleRepository;
import com.datn.finrisk.core.repository.SystemConfigLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
public class RuleService {

    @Autowired 
    private RuleRepository ruleRepository;
    
    @Autowired 
    private SystemConfigLogRepository configLogRepository;
 
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> NUMERIC_OPS  = Set.of(">", ">=", "<", "<=", "==", "!=");
    private static final Set<String> EQUALITY_OPS = Set.of("==", "!=");

    public List<Rule> getAllRules() {
        return ruleRepository.findAll();
    }
 
    @Transactional(rollbackFor = Exception.class)
    public Rule updateRule(Long id, Rule ruleData, String adminUsername) {
        Rule existingRule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Rule với ID: " + id));

        try {
            String oldJson = objectMapper.writeValueAsString(existingRule);
 
            existingRule.setRuleName(ruleData.getRuleName());
            existingRule.setConditions(ruleData.getConditions());
            existingRule.setActionScore(ruleData.getActionScore());
            existingRule.setCategory(ruleData.getCategory());
            if (ruleData.getRuleType() != null) existingRule.setRuleType(ruleData.getRuleType());
 
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(ruleData.getConditions());
                String field = node.has("field") ? node.get("field").asText() : "";
                String op = node.has("operator") ? node.get("operator").asText() : "";
                String val = node.has("value") ? node.get("value").asText() : "";

                validateCondition(field, op, val);

                String generatedSpel = "";
                switch (field) {
                    case "amount": generatedSpel = "#tx.amount " + op + " " + val; break;
                    case "history": generatedSpel = "NEW_RECIPIENT".equals(val) ? "#isNewRecipient " + op + " true" : ""; break;
                    case "suspiciousSession": generatedSpel = "#suspiciousSession " + op + " " + val; break;
                    case "deviceTrusted": generatedSpel = "#deviceTrusted " + op + " " + val; break;
                    case "recentTxCount": generatedSpel = "#recentTxCount " + op + " " + val; break;
                    case "balanceRatio": generatedSpel = "#balanceRatio " + op + " " + val; break;
                    case "isNightTime": generatedSpel = "#isNightTime " + op + " " + val; break;
                    case "emotion": generatedSpel = "#tx.emotionSignal " + op + " '" + val + "'"; break;
                    case "dailyTotal": generatedSpel = "#dailyTotalAmount " + op + " " + val; break;
                }
                existingRule.setSpelExpression(generatedSpel);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception ex) {
                System.err.println("Lỗi phiên dịch JSON sang SpEL: " + ex.getMessage());
            }

            Rule savedRule = ruleRepository.save(existingRule);
            
            String newJson = objectMapper.writeValueAsString(savedRule);
            SystemConfigLog log = new SystemConfigLog(adminUsername, "UPDATE_RULE", "rules", id, oldJson, newJson);
            configLogRepository.save(log);

            return savedRule;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi cập nhật luật: " + e.getMessage());
        }
    }
 
    @Transactional(rollbackFor = Exception.class)
    public Rule toggleRuleStatus(Long id, String adminUsername) {
        Rule existingRule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Rule với ID: " + id));

        try {
            String oldJson = objectMapper.writeValueAsString(existingRule);

 
            existingRule.setIsActive(!existingRule.getIsActive());

            Rule savedRule = ruleRepository.save(existingRule);
            String newJson = objectMapper.writeValueAsString(savedRule);

            SystemConfigLog log = new SystemConfigLog(adminUsername, "TOGGLE_RULE_STATUS", "rules", id, oldJson, newJson);
            configLogRepository.save(log);

            return savedRule;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi bật/tắt luật: " + e.getMessage());
        }
    }
 
    @Transactional(rollbackFor = Exception.class)
    public Rule createRule(Rule ruleData, String adminUsername) {
        try {
            Rule newRule = new Rule();
            newRule.setRuleName(ruleData.getRuleName());
            newRule.setConditions(ruleData.getConditions());
            newRule.setActionScore(ruleData.getActionScore());
            newRule.setIsActive(true);
            newRule.setCategory(ruleData.getCategory());
            newRule.setRuleType(ruleData.getRuleType() != null ? ruleData.getRuleType() : "ADDITIVE");  

 
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(ruleData.getConditions());
                String field = node.has("field") ? node.get("field").asText() : "";
                String op = node.has("operator") ? node.get("operator").asText() : "";
                String val = node.has("value") ? node.get("value").asText() : "";

                validateCondition(field, op, val);

                String generatedSpel = "";
                switch (field) {
                    case "amount": generatedSpel = "#tx.amount " + op + " " + val; break;
                    case "history": generatedSpel = "NEW_RECIPIENT".equals(val) ? "#isNewRecipient " + op + " true" : ""; break;
                    case "suspiciousSession": generatedSpel = "#suspiciousSession " + op + " " + val; break;
                    case "deviceTrusted": generatedSpel = "#deviceTrusted " + op + " " + val; break;
                    case "recentTxCount": generatedSpel = "#recentTxCount " + op + " " + val; break;
                    case "balanceRatio": generatedSpel = "#balanceRatio " + op + " " + val; break;
                    case "isNightTime": generatedSpel = "#isNightTime " + op + " " + val; break;
                    case "emotion": generatedSpel = "#tx.emotionSignal " + op + " '" + val + "'"; break;
                    case "dailyTotal": generatedSpel = "#dailyTotalAmount " + op + " " + val; break;
                }
                newRule.setSpelExpression(generatedSpel);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception ex) {
                System.err.println("Lỗi phiên dịch JSON sang SpEL: " + ex.getMessage());
            }

            Rule savedRule = ruleRepository.save(newRule);
            
            String newJson = objectMapper.writeValueAsString(savedRule);
 
            SystemConfigLog log = new SystemConfigLog(adminUsername, "CREATE_RULE", "rules", savedRule.getId(), "{}", newJson);
            configLogRepository.save(log);

            return savedRule;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo luật mới: " + e.getMessage());
        }
    }

    // ── Whitelist validation ──────────────────────────────────────────────────
    // Called before SpEL generation in both createRule() and updateRule().
    // Throws IllegalArgumentException (propagated as 400) on any invalid input.
    private void validateCondition(String field, String op, String val) {

        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Rule condition must specify a 'field'.");
        }

        switch (field) {

            case "amount":
            case "dailyTotal": {
                if (!NUMERIC_OPS.contains(op)) {
                    throw new IllegalArgumentException(
                        "Operator '" + op + "' is not allowed for field '" + field + "'. " +
                        "Allowed: " + NUMERIC_OPS);
                }
                try {
                    new BigDecimal(val);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                        "Value '" + val + "' is not a valid number for field '" + field + "'.");
                }
                break;
            }

            case "recentTxCount": {
                if (!NUMERIC_OPS.contains(op)) {
                    throw new IllegalArgumentException(
                        "Operator '" + op + "' is not allowed for field 'recentTxCount'. " +
                        "Allowed: " + NUMERIC_OPS);
                }
                try {
                    int n = Integer.parseInt(val);
                    if (n < 0) {
                        throw new IllegalArgumentException(
                            "Value for 'recentTxCount' must be >= 0, got: " + val);
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                        "Value '" + val + "' is not a valid integer for field 'recentTxCount'.");
                }
                break;
            }

            case "balanceRatio": {
                if (!NUMERIC_OPS.contains(op)) {
                    throw new IllegalArgumentException(
                        "Operator '" + op + "' is not allowed for field 'balanceRatio'. " +
                        "Allowed: " + NUMERIC_OPS);
                }
                double d;
                try {
                    d = Double.parseDouble(val);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                        "Value '" + val + "' is not a valid decimal for field 'balanceRatio'.");
                }
                if (d < 0.0 || d > 1.0) {
                    throw new IllegalArgumentException(
                        "Value for 'balanceRatio' must be between 0.0 and 1.0, got: " + val);
                }
                break;
            }

            case "suspiciousSession":
            case "deviceTrusted":
            case "isNightTime": {
                if (!EQUALITY_OPS.contains(op)) {
                    throw new IllegalArgumentException(
                        "Operator '" + op + "' is not allowed for field '" + field + "'. " +
                        "Allowed: " + EQUALITY_OPS);
                }
                if (!"true".equals(val) && !"false".equals(val)) {
                    throw new IllegalArgumentException(
                        "Value for '" + field + "' must be 'true' or 'false', got: '" + val + "'.");
                }
                break;
            }

            case "emotion": {
                if (!EQUALITY_OPS.contains(op)) {
                    throw new IllegalArgumentException(
                        "Operator '" + op + "' is not allowed for field 'emotion'. " +
                        "Allowed: " + EQUALITY_OPS);
                }
                if (val == null || !val.matches("[A-Z][A-Z_]{1,19}")) {
                    throw new IllegalArgumentException(
                        "Value for 'emotion' must be an uppercase name (e.g. HAPPY, FEAR, NEUTRAL), " +
                        "got: '" + val + "'.");
                }
                break;
            }

            case "history": {
                if (!EQUALITY_OPS.contains(op)) {
                    throw new IllegalArgumentException(
                        "Operator '" + op + "' is not allowed for field 'history'. " +
                        "Allowed: " + EQUALITY_OPS);
                }
                if (!"NEW_RECIPIENT".equals(val)) {
                    throw new IllegalArgumentException(
                        "Value for 'history' must be 'NEW_RECIPIENT', got: '" + val + "'.");
                }
                break;
            }

            default:
                throw new IllegalArgumentException(
                    "Unknown field: '" + field + "'. Allowed fields: " +
                    "amount, dailyTotal, recentTxCount, balanceRatio, " +
                    "suspiciousSession, deviceTrusted, isNightTime, emotion, history.");
        }
    }
}