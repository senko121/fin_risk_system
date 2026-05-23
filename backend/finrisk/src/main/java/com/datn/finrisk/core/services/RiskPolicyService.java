package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.RiskPolicy;
import com.datn.finrisk.core.entities.SystemConfigLog;
import com.datn.finrisk.core.repository.RiskPolicyRepository;
import com.datn.finrisk.core.repository.SystemConfigLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RiskPolicyService {

    @Autowired
    private RiskPolicyRepository riskPolicyRepository;

    @Autowired
    private SystemConfigLogRepository configLogRepository;

 
    @Autowired
    private ObjectMapper objectMapper;

 
    public List<RiskPolicy> getAllPolicies() {
        return riskPolicyRepository.findAll();
    }
 
    @Transactional(rollbackFor = Exception.class)
    public RiskPolicy updatePolicyThresholds(Long id, Integer newMin, Integer newMax, String adminUsername) {
        RiskPolicy policy = riskPolicyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Policy với ID: " + id));

 
        if (newMin >= newMax) {
            throw new IllegalArgumentException("Điểm tối thiểu (Min) phải nhỏ hơn điểm tối đa (Max)!");
        }

        try {
 
            String oldJson = objectMapper.writeValueAsString(policy);
 
            policy.setMinScore(newMin);
            policy.setMaxScore(newMax);
            
            RiskPolicy savedPolicy = riskPolicyRepository.save(policy);
 
            String newJson = objectMapper.writeValueAsString(savedPolicy);
 
            SystemConfigLog log = new SystemConfigLog(
                    adminUsername, 
                    "UPDATE_POLICY", 
                    "risk_policies", 
                    id, 
                    oldJson, 
                    newJson
            );
            configLogRepository.save(log);

            return savedPolicy;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi cập nhật Policy: " + e.getMessage());
        }
    }
}