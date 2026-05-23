package com.datn.finrisk.web.controllers;

import com.datn.finrisk.core.entities.RiskPolicy;
import com.datn.finrisk.core.entities.Rule;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.services.RuleService;
import com.datn.finrisk.core.services.AdminUserService;
import com.datn.finrisk.core.services.RiskPolicyService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal; 
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

    @Autowired
    private RuleService ruleService;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private RiskPolicyService riskPolicyService;

 
    @GetMapping("/rules")
    public ResponseEntity<?> getAllRules() {
        try {
            List<Rule> rules = ruleService.getAllRules();
            return ResponseEntity.ok(rules);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi lấy danh sách luật: " + e.getMessage());
        }
    }
 
    @PutMapping("/rules/{id}")
    public ResponseEntity<?> updateRule(@PathVariable Long id, @RequestBody Rule ruleData, Principal principal) {
        try {
            Rule updatedRule = ruleService.updateRule(id, ruleData, principal.getName());
            return ResponseEntity.ok(updatedRule);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

 
    @PatchMapping("/rules/{id}/toggle")
    public ResponseEntity<?> toggleRule(@PathVariable Long id, Principal principal) {
        try {
            Rule toggledRule = ruleService.toggleRuleStatus(id, principal.getName());
            return ResponseEntity.ok(toggledRule);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

 
    @PostMapping("/rules")
    public ResponseEntity<?> createRule(@RequestBody Rule ruleData, Principal principal) {
        try {
            Rule createdRule = ruleService.createRule(ruleData, principal.getName());
            return ResponseEntity.ok(createdRule);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


 
    
    @GetMapping("/dashboard-stats")
    public ResponseEntity<?> getDashboardStats() {
        try {
            long totalTx = transactionRepository.countTotalTransactions();
            long highRiskTx = transactionRepository.countHighRiskTransactions();
            
            BigDecimal totalAmount = transactionRepository.sumTotalSuccessfulAmount();
            if (totalAmount == null) {
                totalAmount = BigDecimal.ZERO;
            }

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalTransactions", totalTx);
            stats.put("highRiskBlocked", highRiskTx);
            stats.put("totalMoneyTransferred", totalAmount);
            
            double riskPercentage = (totalTx == 0) ? 0 : ((double) highRiskTx / totalTx) * 100;
            stats.put("riskPercentage", Math.round(riskPercentage * 100.0) / 100.0);

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi lấy thống kê: " + e.getMessage());
        }
    }

 
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String search) {
        try {
            return ResponseEntity.ok(adminUserService.getUsers(search, page, size));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi lấy danh sách User: " + e.getMessage());
        }
    }

 
    @PatchMapping("/users/{id}/toggle-status")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long id, Principal principal) {
        try {
            return ResponseEntity.ok(adminUserService.toggleUserStatus(id, principal.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

 
    @PatchMapping("/users/{id}/toggle-suspicious")
    public ResponseEntity<?> toggleSuspicious(@PathVariable Long id, Principal principal) {
        try {
            return ResponseEntity.ok(adminUserService.toggleSuspicious(id, principal.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @GetMapping("/users/{id}/recent-transactions")
    public ResponseEntity<?> getUserRecentTransactions(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adminUserService.getRecentTransactionsByUserId(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi truy xuất lịch sử: " + e.getMessage());
        }
    }

 
    @PatchMapping("/users/{id}/reset-face")
    public ResponseEntity<?> resetFaceBiometric(@PathVariable Long id, Principal principal) {
        try {
            return ResponseEntity.ok(adminUserService.resetFaceBiometric(id, principal.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

 

    @GetMapping("/policies")
    public ResponseEntity<?> getAllPolicies() {
        try {
            return ResponseEntity.ok(riskPolicyService.getAllPolicies());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi lấy danh sách Policy: " + e.getMessage());
        }
    }

 
    @PutMapping("/policies/{id}")
    public ResponseEntity<?> updatePolicyThresholds(
            @PathVariable Long id, 
            @RequestBody RiskPolicy policyData, 
            Principal principal) {
        try {
            RiskPolicy updatedPolicy = riskPolicyService.updatePolicyThresholds(
                    id, policyData.getMinScore(), policyData.getMaxScore(), principal.getName());
            return ResponseEntity.ok(updatedPolicy);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}