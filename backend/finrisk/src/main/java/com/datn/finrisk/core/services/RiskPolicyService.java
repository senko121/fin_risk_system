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

    // SỬA DÒNG NÀY: Thay vì new, hãy để Spring Inject vào
    @Autowired
    private ObjectMapper objectMapper;

    // 1. Lấy danh sách toàn bộ các ngưỡng rủi ro
    public List<RiskPolicy> getAllPolicies() {
        return riskPolicyRepository.findAll();
    }

    // 2. Cập nhật ngưỡng điểm Min/Max và Ghi Log
    @Transactional(rollbackFor = Exception.class)
    public RiskPolicy updatePolicyThresholds(Long id, Integer newMin, Integer newMax, String adminUsername) {
        RiskPolicy policy = riskPolicyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Policy với ID: " + id));

        // Kiểm tra logic cơ bản: Min không được lớn hơn Max
        if (newMin >= newMax) {
            throw new IllegalArgumentException("Điểm tối thiểu (Min) phải nhỏ hơn điểm tối đa (Max)!");
        }

        try {
            // Chụp ảnh dữ liệu CŨ
            String oldJson = objectMapper.writeValueAsString(policy);

            // Cập nhật dữ liệu MỚI (Chỉ cho phép sửa min/max, cấm sửa actionBeanName)
            policy.setMinScore(newMin);
            policy.setMaxScore(newMax);
            
            RiskPolicy savedPolicy = riskPolicyRepository.save(policy);

            // Chụp ảnh dữ liệu MỚI
            String newJson = objectMapper.writeValueAsString(savedPolicy);

            // Ghi vết vào Hộp đen (Tab Config Logs)
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