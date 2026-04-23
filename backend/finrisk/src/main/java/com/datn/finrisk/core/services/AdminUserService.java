package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.AdminUserDTO;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    @Autowired 
    private UserRepository userRepository;
    
    @Autowired 
    private AuditLogService auditLogService; // Gọi Thư ký vào ghi sổ

    @Autowired
    private TransactionRepository transactionRepository;

    // 1. Lấy danh sách toàn bộ User (Đã lọc sạch data nhạy cảm)
    public List<AdminUserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // 2. Nút bấm: Khóa / Mở khóa tài khoản (KÈM FORCE LOGOUT)
    @Transactional(rollbackFor = Exception.class)
    public AdminUserDTO toggleUserStatus(Long id, String adminUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy User!"));
        
        String oldStatus = user.getStatus();
        
        // Kiểm tra xem đang Khóa hay Mở
        if ("ACTIVE".equals(oldStatus)) {
            user.setStatus("LOCKED");
            
            //   BÍ QUYẾT ĐÁ VĂNG HACKER Ở ĐÂY: Xóa sạch Token
            // Khi Refresh Token bị null, Hacker gọi API làm mới Token sẽ bị đá văng ra log in lại!
            user.setCurrentRefreshToken(null); 
            
        } else {
            user.setStatus("ACTIVE");
        }
        
        user = userRepository.save(user);

        //   GHI LOG BẰNG CHỨNG
        auditLogService.logAction(adminUsername, "TOGGLE_USER_STATUS", 
            "Đổi trạng thái tài khoản [" + user.getUsername() + "] từ " + oldStatus + " thành " + user.getStatus());

        return convertToDTO(user);
    }

    // 3. Nút bấm: Cảnh báo IP độc hại
    @Transactional(rollbackFor = Exception.class)
    public AdminUserDTO toggleSuspicious(Long id, String adminUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy User!"));
        
        // Đảo ngược cờ cảnh báo
        user.setSuspiciousSession(!user.isSuspiciousSession());
        user = userRepository.save(user);

        //   GHI LOG BẰNG CHỨNG
        String actionMsg = user.isSuspiciousSession() ? "BẬT CẢNH BÁO ĐỎ" : "GỠ CẢNH BÁO";
        auditLogService.logAction(adminUsername, "TOGGLE_SUSPICIOUS_IP", 
            actionMsg + " cho IP/Thiết bị của tài khoản [" + user.getUsername() + "]");

        return convertToDTO(user);
    }


    // 4. Lấy lịch sử 5 giao dịch gần nhất của User
    public List<java.util.Map<String, Object>> getRecentTransactionsByUserId(Long userId) {
        List<Transaction> transactions = transactionRepository.findTop5ByFromAccountUserIdOrderByCreatedAtDesc(userId);
        
        return transactions.stream().map(tx -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", "TX" + tx.getId());
            map.put("amount", tx.getAmount());
            map.put("status", tx.getStatus());
            map.put("riskScore", tx.getTotalRiskScore());
            
            // Format lại ngày tháng cho đẹp
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            map.put("date", tx.getCreatedAt().format(formatter));
            
            return map;
        }).collect(Collectors.toList());
    }

    // Hàm tiện ích: Ép kiểu từ Entity sang DTO
    private AdminUserDTO convertToDTO(User user) {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFullName(user.getFullName());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setEmail(user.getEmail());
        dto.setStatus(user.getStatus());
        dto.setSuspiciousSession(user.isSuspiciousSession());
        dto.setLastLoginIp(user.getLastLoginIp());
        dto.setLastLoginDevice(user.getLastLoginDevice());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}