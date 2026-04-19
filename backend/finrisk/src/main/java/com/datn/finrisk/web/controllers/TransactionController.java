package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.AuthVerifyRequest;
import com.datn.finrisk.application.dtos.FaceAIResponse;
import com.datn.finrisk.application.dtos.TransactionRequest;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.OtpService;
import com.datn.finrisk.core.services.TransactionService;
import com.datn.finrisk.core.services.AuditLogService; // 🚀 IMPORT THƯ KÝ
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.datn.finrisk.core.repository.TransactionLedgerRepository;
import com.datn.finrisk.core.entities.TransactionLedger;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.entities.Account;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "http://localhost:5173") 
public class TransactionController {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionLedgerRepository ledgerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private com.datn.finrisk.core.services.EmailService emailService;

    // 🚀 GỌI THƯ KÝ VÀO GHI SỔ GIAO DỊCH
    @Autowired
    private AuditLogService auditLogService;

    @PostMapping("/process")
    public ResponseEntity<?> processTransaction(@RequestBody TransactionRequest request) {
        try {
            Transaction result = transactionService.initiateTransaction(
                    request.getFromAccountId(),
                    request.getToAccount(),
                    request.getAmount(),
                    request.getEmotion()
            );

            // 🚀 GHI LOG TẠO LỆNH THÀNH CÔNG (Nhưng chưa chốt tiền)
            String username = result.getFromAccount().getUser().getUsername();
            auditLogService.logAction(username, "TRANSACTION_INITIATED", "Tạo lệnh chuyển " + request.getAmount() + " VND đến STK " + request.getToAccount() + ". Mức rủi ro: " + result.getRiskLevel());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
        
    @PostMapping("/verify")
    public ResponseEntity<?> verifyAndExecute(@RequestBody AuthVerifyRequest request) {
        try {
            Transaction tx = transactionRepository.findById(request.getTransactionId())
                    .orElseThrow(() -> new RuntimeException("Giao dịch không tồn tại!"));

            String username = tx.getFromAccount().getUser().getUsername();

            // ==========================================================
            // LUỒNG 1: XỬ LÝ NHẬP OTP (CHỐT SỔ TẠI ĐÂY)
            // ==========================================================
            if (request.getAuthType().equals("OTP")) {
                boolean isValid = otpService.verifyOtp(tx.getId(), request.getAuthCode());

                if (!isValid) {
                    // 🚀 GHI LOG: NHẬP SAI OTP
                    auditLogService.logAction(username, "OTP_VERIFY_FAILED", "Nhập sai mã OTP cho giao dịch " + tx.getId());
                    return ResponseEntity.badRequest().body("OTP sai hoặc đã hết hạn!");
                }

                Transaction completedTx = transactionService.executeLedgerTransaction(tx.getId());
                
                // 🚀 GHI LOG: CHỐT SỔ THÀNH CÔNG QUA ẢI OTP
                auditLogService.logAction(username, "TRANSACTION_SUCCESS", "Chuyển thành công " + tx.getAmount() + " VND (Xác thực qua OTP). ID Giao dịch: " + tx.getId());
                
                return ResponseEntity.ok(completedTx);
            }

            // ==========================================================
            // LUỒNG 2: XỬ LÝ QUÉT MẶT (CHƯA CHỐT SỔ, CHUYỂN TIẾP SANG OTP)
            // ==========================================================
            else if (request.getAuthType().equals("FACE")) {

                String liveImage = request.getFaceImageBase64();
                String registeredImage = tx.getFromAccount().getUser().getBase64FaceImage();

                if (registeredImage == null || registeredImage.isEmpty()) {
                    return ResponseEntity.badRequest().body("Chưa đăng ký khuôn mặt!");
                }

                String pythonUrl = "http://localhost:5000/api/ai/verify-face";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, String> body = new HashMap<>();
                body.put("live_image_base64", liveImage);
                body.put("registered_image_base64", registeredImage);

                HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);
                RestTemplate restTemplate = new RestTemplate();

                FaceAIResponse aiResponse = restTemplate.postForObject(pythonUrl, requestEntity, FaceAIResponse.class);

                if (aiResponse == null || !aiResponse.isMatched()) {
                    // 🚀 GHI LOG: QUÉT MẶT SAI NGƯỜI
                    auditLogService.logAction(username, "FACE_SCAN_FAILED", "Kẻ lạ mặt cố gắng xác thực giao dịch " + tx.getId() + ". AI từ chối khớp ảnh.");
                    return ResponseEntity.badRequest().body("Face không khớp!");
                }

                // 🚀 GHI LOG: QUÉT MẶT ĐÚNG CHÍNH CHỦ
                auditLogService.logAction(username, "FACE_SCAN_SUCCESS", "Xác thực khuôn mặt thành công. Chuyển tiếp sang vòng OTP bảo mật kép.");

                tx.setStatus("PENDING_OTP");
                transactionRepository.save(tx);

                String newOtp = String.format("%06d", new java.util.Random().nextInt(999999));
                otpService.saveOtp(tx.getId(), newOtp);
                
                try {
                    String userEmail = tx.getFromAccount().getUser().getEmail();
                    emailService.sendOtpEmail(userEmail, newOtp);
                } catch (Exception e) {
                    System.err.println("❌ Lỗi gửi email lớp 2: " + e.getMessage());
                }

                return ResponseEntity.ok(tx);
            }

            return ResponseEntity.badRequest().body("Loại xác thực không hợp lệ!");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ... (Giữ nguyên các hàm lấy Lịch sử, Tra cứu tên, Recent Recipients bên dưới) ...
    @GetMapping("/history/{accountId}")
        public ResponseEntity<?> getTransactionHistory(@PathVariable Long accountId) {
            try {
            List<TransactionLedger> ledgers = ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
            List<Map<String, Object>> result = ledgers.stream().map(l -> {
                Map<String, Object> map = new HashMap<>();
                
                // Dữ liệu Sổ cái (Kế toán)
                map.put("id", l.getId());
                map.put("type", l.getEntryType()); 
                map.put("amount", l.getAmount());
                map.put("balanceAfter", l.getBalanceAfter());
                map.put("date", l.getCreatedAt());
                map.put("description", l.getDescription() != null ? l.getDescription() : (l.getEntryType().equals("DEBIT") ? "Chuyển tiền đi" : "Nhận tiền đến"));
                
                // 🚀 BỔ SUNG DỮ LIỆU TỪ BẢNG GIAO DỊCH GỐC (AI & Security)
                Transaction rootTx = l.getTransaction();
                if (rootTx != null) {
                    map.put("toAccountNumber", rootTx.getToAccountNumber());
                    map.put("riskLevel", rootTx.getRiskLevel());
                    map.put("totalRiskScore", rootTx.getTotalRiskScore());
                    map.put("emotionSignal", rootTx.getEmotionSignal());
                } else {
                    // Fallback nếu không có transaction gốc (VD: tiền nạp ban đầu)
                    map.put("toAccountNumber", "N/A");
                    map.put("riskLevel", "LOW");
                    map.put("totalRiskScore", 0);
                    map.put("emotionSignal", "N/A");
                }

                return map;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi lấy lịch sử: " + e.getMessage());
        }
    }

    @GetMapping("/lookup/{accountNumber}")
    public ResponseEntity<?> lookupAccountName(@PathVariable String accountNumber) {
        try {
            Account account = accountRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại trên hệ thống!"));
            Map<String, String> response = new HashMap<>();
            response.put("fullName", account.getUser().getFullName()); 
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/recent-recipients/{accountId}")
    public ResponseEntity<?> getRecentRecipients(@PathVariable Long accountId) {
        try {
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            List<TransactionLedger> recentLedgers = ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
            List<Map<String, String>> recipients = recentLedgers.stream()
                .filter(l -> l.getEntryType().equals("DEBIT")) 
                .filter(l -> l.getCreatedAt().isAfter(sevenDaysAgo)) 
                .map(l -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("accountNumber", l.getTransaction().getToAccountNumber());
                    String name = accountRepository.findByAccountNumber(l.getTransaction().getToAccountNumber())
                                    .map(acc -> acc.getUser().getFullName())
                                    .orElse("Người nhận ngoài hệ thống");
                    map.put("fullName", name);
                    return map;
                })
                .distinct() 
                .limit(5)   
                .collect(Collectors.toList());
            return ResponseEntity.ok(recipients);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi lấy danh sách gần đây: " + e.getMessage());
        }
    }
}