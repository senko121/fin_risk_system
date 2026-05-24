 

package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.AdminTransactionDTO;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.exceptions.BusinessLogicException;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.AdminTransactionService;
import com.datn.finrisk.core.services.AuditLogService;
import com.datn.finrisk.core.services.TransactionService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/transactions")
public class AdminTransactionController {

    @Autowired
    private AdminTransactionService adminTransactionService;

 
    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AuditLogService auditLogService;
 
    @GetMapping
    public ResponseEntity<?> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel) {
        try {
 
            Page<AdminTransactionDTO> result = adminTransactionService.getTransactions(page, size, search, status, riskLevel);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
 
            e.printStackTrace(); 
            return ResponseEntity.badRequest().body("Lỗi tải danh sách giao dịch: " + e.getMessage());
        }
    }
 
    @PostMapping("/{txId}/resolve-review")
    public ResponseEntity<?> resolveUnderReviewTransaction(
            @PathVariable Long txId,
            @RequestParam String action,  
            @RequestParam String adminNotes,
            HttpServletRequest request) {

 

        try {
            Transaction tx = transactionRepository.findById(txId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch với ID: " + txId));

            String currentStatus = tx.getStatus();
            String customerUsername = tx.getFromAccount().getUser().getUsername();

            switch (action.toUpperCase()) {
                case "APPROVE":
 
                    if (!"UNDER_REVIEW".equals(currentStatus)) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "status", "ERROR",
                                "message", "Lỗi: Chỉ có thể APPROVE giao dịch đang ở trạng thái UNDER_REVIEW."
                        ));
                    }
                    Transaction completedTx = transactionService.executeTransactionCore(tx);
                    auditLogService.logAction("ADMIN", "RESOLVE_REVIEW_APPROVE", "Admin đã DUYỆT giao dịch " + txId + ". Ghi chú: " + adminNotes);
                    
                    return ResponseEntity.ok(Map.of(
                            "status", "SUCCESS", 
                            "message", "Đã duyệt và trừ tiền thành công.", 
                            "data", completedTx
                    ));

                case "REJECT_FRAUD":
                    // Fast-fail: obvious wrong state (admin has a stale but non-concurrent view)
                    if (!"UNDER_REVIEW".equals(currentStatus)) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "status", "ERROR",
                                "message", "Lỗi: Chỉ có thể REJECT_FRAUD giao dịch đang ở trạng thái UNDER_REVIEW."
                        ));
                    }
                    // Atomic guard: only transitions UNDER_REVIEW → BLOCKED at DB level.
                    // Returns 0 if another admin already changed the status (APPROVE won the race).
                    int blocked = transactionRepository.blockIfUnderReview(txId);
                    if (blocked == 0) {
                        return ResponseEntity.status(409).body(Map.of(
                                "status", "CONFLICT",
                                "message", "Giao dịch vừa được xử lý bởi admin khác. Vui lòng tải lại trang để xem trạng thái mới nhất."
                        ));
                    }

                    auditLogService.logAction("ADMIN", "RESOLVE_REVIEW_REJECT", "Đánh dấu LỪA ĐẢO giao dịch " + txId + ". Ghi chú: " + adminNotes);

                    return ResponseEntity.ok(Map.of(
                            "status", "BLOCKED",
                            "message", "Đã khóa giao dịch để bảo vệ tài sản khách hàng."
                    ));

                case "REVERSE":
                    if (!"SUCCESS".equals(currentStatus)) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "status", "ERROR",
                                "message", "Lỗi: Nút REVERSE chỉ dùng để hoàn tác giao dịch đã SUCCESS."
                        ));
                    }
                    transactionService.executeReversalCore(tx);
                    auditLogService.logAction("ADMIN", "TRANSACTION_REVERSED",
                        "Admin HOÀN TIỀN giao dịch " + txId + ". Ghi chú: " + adminNotes);
                    return ResponseEntity.ok(Map.of(
                            "status", "REVERSED",
                            "message", "Đã hoàn tác giao dịch, tiền đã được lệnh trả về tài khoản gửi."
                    ));

                default:
                    return ResponseEntity.badRequest().body(Map.of(
                            "status", "ERROR",
                            "message", "Hành động (Action) không hợp lệ. Hãy truyền lên APPROVE, REJECT_FRAUD, hoặc REVERSE."
                    ));
            }
        } catch (BusinessLogicException e) {
            // ERR_DUPLICATE_EXECUTION: claimForExecution returned 0 — another admin approved or
            // rejected this transaction between our findById and the atomic claim inside executeTransactionCore.
            if ("ERR_DUPLICATE_EXECUTION".equals(e.getErrorCode())) {
                return ResponseEntity.status(409).body(Map.of(
                        "status", "CONFLICT",
                        "message", "Giao dịch vừa được xử lý bởi admin khác. Vui lòng tải lại trang để xem trạng thái mới nhất."
                ));
            }
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "SERVER_ERROR",
                    "message", "Lỗi nghiệp vụ: " + e.getMessage()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "SERVER_ERROR",
                    "message", "Lỗi hệ thống khi xử lý giao dịch: " + e.getMessage()
            ));
        }
    }
 
    @GetMapping("/{txId}/risk-details")
    public ResponseEntity<?> getTransactionRiskDetails(@PathVariable Long txId) {
 
        try {
            com.datn.finrisk.application.dtos.TransactionRiskDetailDTO detail = 
                adminTransactionService.getTransactionRiskDetail(txId);
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi truy xuất hồ sơ rủi ro: " + e.getMessage());
        }
    }
}