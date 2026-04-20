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
    
    // Công cụ giúp biến Java Object thành chuỗi JSON
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 1. Lấy tất cả danh sách Luật (Cả bật và tắt) để hiển thị lên bảng
    public List<Rule> getAllRules() {
        return ruleRepository.findAll();
    }

    // 2. Cập nhật thông tin Luật và Ghi vết lịch sử
    @Transactional(rollbackFor = Exception.class)
    public Rule updateRule(Long id, Rule ruleData, String adminUsername) {
        Rule existingRule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Rule với ID: " + id));

        try {
            // Chụp ảnh Old Value
            String oldJson = objectMapper.writeValueAsString(existingRule);

            // Cập nhật dữ liệu
            existingRule.setRuleName(ruleData.getRuleName());
            existingRule.setConditions(ruleData.getConditions()); // Chuỗi JSON từ Frontend gửi xuống
            existingRule.setActionScore(ruleData.getActionScore());

            Rule savedRule = ruleRepository.save(existingRule);
            
            // Chụp ảnh New Value
            String newJson = objectMapper.writeValueAsString(savedRule);

            // Ghi vào hộp đen SystemConfigLog
            SystemConfigLog log = new SystemConfigLog(adminUsername, "UPDATE_RULE", "rules", id, oldJson, newJson);
            configLogRepository.save(log);

            return savedRule;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi cập nhật luật: " + e.getMessage());
        }
    }

    // 3. Công tắc Bật/Tắt Luật nhanh (Kèm ghi lịch sử)
    @Transactional(rollbackFor = Exception.class)
    public Rule toggleRuleStatus(Long id, String adminUsername) {
        Rule existingRule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Rule với ID: " + id));

        try {
            String oldJson = objectMapper.writeValueAsString(existingRule);

            // Đảo ngược trạng thái hiện tại (Đang bật -> Tắt, Đang tắt -> Bật)
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
}