package com.datn.finrisk.web.controllers;

import com.datn.finrisk.core.entities.Role; // Nhớ import cái Enum này
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:5173")
public class AdminController {

    @Autowired
    private TransactionRepository transactionRepository;

    // Phải gọi UserRepository ra để lấy thông tin thằng đang request
    @Autowired
    private UserRepository userRepository;

    @GetMapping("/dashboard-stats")
    // Bắt buộc React phải nhét ID của User vào Header "X-User-Id"
    public ResponseEntity<?> getDashboardStats() {
        try {

            // 3. Vượt qua trạm gác -> Bắt đầu tính toán số liệu tuyệt mật
            long totalTx = transactionRepository.countTotalTransactions();
            long highRiskTx = transactionRepository.countHighRiskTransactions();
            
            BigDecimal totalAmount = transactionRepository.sumTotalSuccessfulAmount();
            if (totalAmount == null) {
                totalAmount = BigDecimal.ZERO;
            }

            // Đóng gói dữ liệu để gửi về React vẽ biểu đồ
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalTransactions", totalTx);
            stats.put("highRiskBlocked", highRiskTx);
            stats.put("totalMoneyTransferred", totalAmount);
            
            // Tính phần trăm rủi ro (để vẽ Chart tròn)
            double riskPercentage = (totalTx == 0) ? 0 : ((double) highRiskTx / totalTx) * 100;
            stats.put("riskPercentage", Math.round(riskPercentage * 100.0) / 100.0); // Làm tròn 2 chữ số

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi lấy thống kê: " + e.getMessage());
        }
    }
}