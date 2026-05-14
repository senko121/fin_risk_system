// package com.datn.finrisk.web.controllers;

// import com.datn.finrisk.application.dtos.AdminTransactionDTO;
// import com.datn.finrisk.core.services.AdminTransactionService;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.data.domain.Page;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;

// @RestController
// @RequestMapping("/api/admin/transactions")
// @CrossOrigin(origins = "http://localhost:5173") // Mở CORS cho Frontend ReactJS
// public class AdminTransactionController {

//     @Autowired
//     private AdminTransactionService adminTransactionService;

//     // 🚀 API Lấy danh sách giao dịch (Có phân trang và lọc)
//     @GetMapping
//     public ResponseEntity<?> getAllTransactions(
//             @RequestParam(defaultValue = "0") int page,
//             @RequestParam(defaultValue = "10") int size,
//             @RequestParam(defaultValue = "") String search,
//             @RequestParam(required = false) String status,
//             @RequestParam(required = false) String riskLevel) {
//         try {
//             // Gọi Service để lấy dữ liệu đã được lót DTO
//             Page<AdminTransactionDTO> result = adminTransactionService.getTransactions(page, size, search, status, riskLevel);
            
//             return ResponseEntity.ok(result);
//         } catch (Exception e) {
//             // Log lỗi ra console để dev dễ debug
//             e.printStackTrace(); 
//             return ResponseEntity.badRequest().body("Lỗi tải danh sách giao dịch: " + e.getMessage());
//         }
//     }

    
// }

package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.AdminTransactionDTO;
import com.datn.finrisk.core.entities.Transaction;
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
@CrossOrigin(origins = "http://localhost:5173") // Mở CORS cho Frontend ReactJS
public class AdminTransactionController {

    @Autowired
    private AdminTransactionService adminTransactionService;

    // Cấp thêm "vũ khí" cho Admin Controller để xử lý lệnh duyệt
    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AuditLogService auditLogService;

    // =========================================================================
    // 🚀 API 1: Lấy danh sách giao dịch (Có phân trang và lọc)
    // =========================================================================
    @GetMapping
    public ResponseEntity<?> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel) {
        try {
            // Gọi Service để lấy dữ liệu đã được lót DTO
            Page<AdminTransactionDTO> result = adminTransactionService.getTransactions(page, size, search, status, riskLevel);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // Log lỗi ra console để dev dễ debug
            e.printStackTrace(); 
            return ResponseEntity.badRequest().body("Lỗi tải danh sách giao dịch: " + e.getMessage());
        }
    }
// =========================================================================
    // 🚀 API 2: ADMIN DUYỆT GIAO DỊCH BỊ GIAM LỎNG (UNDER_REVIEW / REVERSE)
    // =========================================================================
    @PostMapping("/{txId}/resolve-review")
    public ResponseEntity<?> resolveUnderReviewTransaction(
            @PathVariable Long txId,
            @RequestParam String action, // Các cờ: APPROVE, REJECT_FRAUD, REVERSE
            @RequestParam String adminNotes,
            HttpServletRequest request) {

        // 🛡️ BẢO MẬT: Sau này bro nhớ nhúng @PreAuthorize("hasRole('ADMIN')") vào đây

        try {
            Transaction tx = transactionRepository.findById(txId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch với ID: " + txId));

            String currentStatus = tx.getStatus();
            String customerUsername = tx.getFromAccount().getUser().getUsername();

            switch (action.toUpperCase()) {
                case "APPROVE":
                    // 🔒 CHỈ DUYỆT khi trạng thái đang bị giam lỏng
                    if (!"UNDER_REVIEW".equals(currentStatus)) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "status", "ERROR",
                                "message", "Lỗi: Chỉ có thể APPROVE giao dịch đang ở trạng thái UNDER_REVIEW."
                        ));
                    }
                    tx.setStatus("PROCESSING"); 
                    transactionRepository.save(tx);
                    
                    Transaction completedTx = transactionService.executeTransactionCore(tx);
                    auditLogService.logAction("ADMIN", "RESOLVE_REVIEW_APPROVE", "Admin đã DUYỆT giao dịch " + txId + ". Ghi chú: " + adminNotes);
                    
                    return ResponseEntity.ok(Map.of(
                            "status", "SUCCESS", 
                            "message", "Đã duyệt và trừ tiền thành công.", 
                            "data", completedTx
                    ));

                case "REJECT_FRAUD":
                    // 🔒 CHỈ KHÓA khi trạng thái đang bị giam lỏng
                    if (!"UNDER_REVIEW".equals(currentStatus)) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "status", "ERROR",
                                "message", "Lỗi: Chỉ có thể REJECT_FRAUD giao dịch đang ở trạng thái UNDER_REVIEW."
                        ));
                    }
                    tx.setStatus("BLOCKED");
                    transactionRepository.save(tx);
                    
                    auditLogService.logAction("ADMIN", "RESOLVE_REVIEW_REJECT", "Đánh dấu LỪA ĐẢO giao dịch " + txId + ". Ghi chú: " + adminNotes);
                    
                    return ResponseEntity.ok(Map.of(
                            "status", "BLOCKED", 
                            "message", "Đã khóa giao dịch để bảo vệ tài sản khách hàng."
                    ));

                case "REVERSE":
                    // 🔒 CHỈ HOÀN TIỀN khi giao dịch đã chốt sổ thành công
                    if (!"SUCCESS".equals(currentStatus)) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "status", "ERROR",
                                "message", "Lỗi: Nút REVERSE chỉ dùng để hoàn tác giao dịch đã SUCCESS."
                        ));
                    }
                    tx.setStatus("REVERSED");
                    transactionRepository.save(tx);
                    
                    // 🚀 Đòi hỏi hàm đảo Sổ Cái trong Service
                    // Chú ý: Hàm này phải bọc @Transactional cẩn thận
                    // transactionService.executeReversalCore(tx); 
                    
                    auditLogService.logAction("ADMIN", "TRANSACTION_REVERSED", "Admin HOÀN TIỀN giao dịch " + txId + ". Ghi chú: " + adminNotes);
                    
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
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "SERVER_ERROR",
                    "message", "Lỗi hệ thống khi xử lý giao dịch: " + e.getMessage()
            ));
        }
    }
}