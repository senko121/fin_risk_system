package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Rule;
import com.datn.finrisk.core.entities.SystemConfigLog;
import com.datn.finrisk.core.repository.RuleRepository;
import com.datn.finrisk.core.repository.SystemConfigLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RuleService {

    @Autowired 
    private RuleRepository ruleRepository;
    
    @Autowired 
    private SystemConfigLogRepository configLogRepository;
 
    private final ObjectMapper objectMapper = new ObjectMapper();
 
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
 
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(ruleData.getConditions());
                String field = node.has("field") ? node.get("field").asText() : "";
                String op = node.has("operator") ? node.get("operator").asText() : "";
                String val = node.has("value") ? node.get("value").asText() : "";

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

 
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(ruleData.getConditions());
                String field = node.has("field") ? node.get("field").asText() : "";
                String op = node.has("operator") ? node.get("operator").asText() : "";
                String val = node.has("value") ? node.get("value").asText() : "";

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
}