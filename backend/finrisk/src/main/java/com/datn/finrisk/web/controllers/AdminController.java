

package com.datn.finrisk.web.controllers;

import com.datn.finrisk.core.entities.Role;
import com.datn.finrisk.core.entities.Rule;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.services.RuleService;
import com.datn.finrisk.core.services.AdminUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:5173")
public class AdminController {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    //   BƠM THÊM RULE SERVICE VÀO ĐÂY
    @Autowired
    private RuleService ruleService;

    @Autowired
    private AdminUserService adminUserService;

    // ==========================================================
    // PHẦN 1: CÁC API QUẢN LÝ RULE ENGINE ĐỘNG (THÊM MỚI)
    // ==========================================================

    // API 1: Lấy danh sách tất cả các luật
    @GetMapping("/rules")
    public ResponseEntity<?> getAllRules() {
        try {
            List<Rule> rules = ruleService.getAllRules();
            return ResponseEntity.ok(rules);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi lấy danh sách luật: " + e.getMessage());
        }
    }

    // API 2: Cập nhật nội dung luật (Điều kiện, Tên, Điểm)
    @PutMapping("/rules/{id}")
    public ResponseEntity<?> updateRule(@PathVariable Long id, @RequestBody Rule ruleData, 
            @RequestHeader(value="X-Admin-Username", defaultValue="admin_root") String adminUsername) {
        try {
            // Header X-Admin-Username dùng để React truyền tên người đang đăng nhập lên lưu log
            Rule updatedRule = ruleService.updateRule(id, ruleData, adminUsername);
            return ResponseEntity.ok(updatedRule);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // API 3: Bật / Tắt một luật (Toggle Soft Delete)
    @PatchMapping("/rules/{id}/toggle")
    public ResponseEntity<?> toggleRule(@PathVariable Long id, 
            @RequestHeader(value="X-Admin-Username", defaultValue="admin_root") String adminUsername) {
        try {
            Rule toggledRule = ruleService.toggleRuleStatus(id, adminUsername);
            return ResponseEntity.ok(toggledRule);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    // ==========================================================
    // PHẦN 2: API THỐNG KÊ DASHBOARD (CỦA BRO GIỮ NGUYÊN)
    // ==========================================================
    
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

    // ==========================================================
    // PHẦN 3: CÁC API QUẢN LÝ NGƯỜI DÙNG (THÊM MỚI)
    // ==========================================================

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        try {
            return ResponseEntity.ok(adminUserService.getAllUsers());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/users/{id}/toggle-status")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long id,
            @RequestHeader(value="X-Admin-Username", defaultValue="admin_root") String adminUsername) {
        try {
            return ResponseEntity.ok(adminUserService.toggleUserStatus(id, adminUsername));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/users/{id}/toggle-suspicious")
    public ResponseEntity<?> toggleSuspicious(@PathVariable Long id,
            @RequestHeader(value="X-Admin-Username", defaultValue="admin_root") String adminUsername) {
        try {
            return ResponseEntity.ok(adminUserService.toggleSuspicious(id, adminUsername));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    // lấy giao dich cua user
    @GetMapping("/users/{id}/recent-transactions")
    public ResponseEntity<?> getUserRecentTransactions(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adminUserService.getRecentTransactionsByUserId(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi truy xuất lịch sử: " + e.getMessage());
        }
    }
}