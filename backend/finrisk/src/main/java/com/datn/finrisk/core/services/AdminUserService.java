package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.AdminUserDTO;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.SystemConfigLog;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.repository.SystemConfigLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    @Autowired 
    private UserRepository userRepository;
    
    @Autowired 
    private AuditLogService auditLogService;  

    @Autowired
    private SystemConfigLogRepository configLogRepository;  

    @Autowired
    private TransactionRepository transactionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper(); 
    
    public Page<AdminUserDTO> getUsers(String search, int page, int size) {
 
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
 
            Page<User> userPage = userRepository.searchUsers(search, pageable);
 
            return userPage.map(this::convertToDTO);
        }

 
    @Transactional(rollbackFor = Exception.class)
    public AdminUserDTO toggleUserStatus(Long id, String adminUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy User!"));
        
        String oldStatus = user.getStatus();
        
 
        if ("ACTIVE".equals(oldStatus)) {
            user.setStatus("LOCKED");
 
            user.setCurrentRefreshToken(null); 
        } else {
            user.setStatus("ACTIVE");
        }
        
        user = userRepository.save(user);
 
        auditLogService.logAction(adminUsername, "TOGGLE_USER_STATUS", 
            "Đổi trạng thái tài khoản [" + user.getUsername() + "] từ " + oldStatus + " thành " + user.getStatus());
 
        try {
            String oldJson = objectMapper.writeValueAsString(Map.of("status", oldStatus));
            String newJson = objectMapper.writeValueAsString(Map.of("status", user.getStatus()));
            SystemConfigLog configLog = new SystemConfigLog(adminUsername, "TOGGLE_USER_STATUS", "users", id, oldJson, newJson);
            configLogRepository.save(configLog);
        } catch (Exception e) {
            System.err.println("Lỗi ghi log Config: " + e.getMessage());
        }

        return convertToDTO(user);
    }
 
    @Transactional(rollbackFor = Exception.class)
    public AdminUserDTO toggleSuspicious(Long id, String adminUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy User!"));
        
        boolean oldFlag = user.isAdminFlagged();
 
        user.setAdminFlagged(!oldFlag);
        user = userRepository.save(user);
 
        String actionMsg = user.isAdminFlagged() ? "BẬT CẢNH BÁO ĐỎ (Thủ công)" : "GỠ CẢNH BÁO (Thủ công)";
        auditLogService.logAction(adminUsername, "TOGGLE_ADMIN_FLAG", 
            actionMsg + " cho tài khoản [" + user.getUsername() + "]");
 
        try {
            String oldJson = objectMapper.writeValueAsString(Map.of("adminFlagged", oldFlag));
            String newJson = objectMapper.writeValueAsString(Map.of("adminFlagged", user.isAdminFlagged()));
            SystemConfigLog configLog = new SystemConfigLog(adminUsername, "TOGGLE_ADMIN_FLAG", "users", id, oldJson, newJson);
            configLogRepository.save(configLog);
        } catch (Exception e) {
            System.err.println("Lỗi ghi log Config: " + e.getMessage());
        }

        return convertToDTO(user);
    }
 
    public List<Map<String, Object>> getRecentTransactionsByUserId(Long userId) {
        List<Transaction> transactions = transactionRepository.findTop5ByFromAccountUserIdOrderByCreatedAtDesc(userId);
        
        return transactions.stream().map(tx -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", "TX" + tx.getId());
            map.put("amount", tx.getAmount());
            map.put("status", tx.getStatus());
            map.put("riskScore", tx.getTotalRiskScore());
            
 
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            map.put("date", tx.getCreatedAt().format(formatter));
            
            return map;
        }).collect(Collectors.toList());
    }
 
    @Transactional(rollbackFor = Exception.class)
    public AdminUserDTO resetFaceBiometric(Long id, String adminUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy User!"));

    if (!user.hasFaceEmbedding() && !user.hasLegacyFaceImage()) {
            throw new RuntimeException("Người dùng này chưa đăng ký khuôn mặt!");
        }
        user.setFaceEmbedding(null);           // xóa embedding mới
        user.setBase64FaceImage(null);    
        user = userRepository.save(user);

 
        auditLogService.logAction(adminUsername, "RESET_FACE_DATA", 
            "Hủy vĩnh viễn dữ liệu sinh trắc học khuôn mặt của tài khoản [" + user.getUsername() + "]");

 
        try {
 
            String oldJson = objectMapper.writeValueAsString(Map.of("base64FaceImage", "[DỮ_LIỆU_ẢNH_ĐÃ_BỊ_HỦY]"));
            String newJson = objectMapper.writeValueAsString(Map.of("base64FaceImage", "null"));
            
            SystemConfigLog configLog = new SystemConfigLog(adminUsername, "RESET_FACE_DATA", "users", id, oldJson, newJson);
            configLogRepository.save(configLog);
        } catch (Exception e) {
            System.err.println("Lỗi ghi log Config: " + e.getMessage());
        }

        return convertToDTO(user);
    }
 
    private AdminUserDTO convertToDTO(User user) {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFullName(user.getFullName());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setEmail(user.getEmail());
        dto.setStatus(user.getStatus());
        
        dto.setSuspiciousSession(user.isSuspiciousSession() || user.isAdminFlagged());
        
        dto.setLastLoginIp(user.getLastLoginIp());
        dto.setLastLoginDevice(user.getLastLoginDevice());
        dto.setCreatedAt(user.getCreatedAt());
 
          boolean hasFace = user.hasFaceEmbedding() || user.hasLegacyFaceImage();
        dto.setHasFaceData(hasFace);

        return dto;
    }


    
}