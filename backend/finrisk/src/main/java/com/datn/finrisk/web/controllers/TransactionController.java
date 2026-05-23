 package com.datn.finrisk.web.controllers;

import com.datn.finrisk.application.dtos.AuthVerifyRequest;
import com.datn.finrisk.application.dtos.FaceAIResponse;
import com.datn.finrisk.application.dtos.TransactionRequest;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.OtpService;
import com.datn.finrisk.core.services.TransactionService;
import com.datn.finrisk.core.strategies.AdvancedFaceActionStrategy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.datn.finrisk.core.services.AuditLogService; //   IMPORT THƯ KÝ
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
 import org.springframework.data.domain.Page;
 import org.springframework.data.domain.PageRequest;
 import org.springframework.data.domain.Pageable;
 import org.springframework.data.domain.Sort;
 import java.util.Set;

import com.datn.finrisk.core.repository.TransactionLedgerRepository;
import com.datn.finrisk.core.entities.TransactionLedger;
import com.datn.finrisk.core.entities.UserSecurity;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.services.EmailService;
import com.datn.finrisk.core.services.PinService;
import com.datn.finrisk.core.services.RiskEvaluationService;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/transactions")
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
    private EmailService emailService;

    @Autowired private AdvancedFaceActionStrategy faceScanActionStrategy;

    @Autowired
    private PinService pinService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private RiskEvaluationService riskEvaluationService;
 
    //Transaction B1: Nhan yêu cầu khởi tạo giao dịch -> Transaction B2: Gọi AccountRepository 
    @PostMapping("/process")
    public ResponseEntity<?> processTransaction(@Valid @RequestBody TransactionRequest request, HttpServletRequest httpRequest) throws Exception {
        
        String currentIp = httpRequest.getRemoteAddr();
        String currentDevice = httpRequest.getHeader("User-Agent");
        
        if (currentDevice != null && currentDevice.length() > 250) {
            currentDevice = currentDevice.substring(0, 250);
        }
 
        Transaction result = transactionService.initiateTransaction(
                request.getFromAccountId(),
                request.getToAccount(),
                request.getAmount(),
                request.getDescription(),
                currentIp,      
                currentDevice   
        );
 
        if (request.getCreatedAt() != null) {
            result.setCreatedAt(request.getCreatedAt());
 
            result = transactionRepository.save(result);
            log.info("⏰ [MOCK TIME] Đã ép thời gian giao dịch {} về mốc giả lập: {}", result.getId(), request.getCreatedAt());
        }

        String username = result.getFromAccount().getUser().getUsername();
        auditLogService.logAction(username, "TRANSACTION_INITIATED", "Tạo lệnh chuyển " + request.getAmount() + " VND đến STK " + request.getToAccount() + ". Mức rủi ro: " + result.getRiskLevel());

        return ResponseEntity.ok(result);
    }

//Transaction B1 Phase2: Thực hiện xác thực mã pin -> Transaction B5: TransactionRepository
    @PostMapping("/verify")
    public ResponseEntity<?> verifyAndExecute(@Valid @RequestBody AuthVerifyRequest request) throws Exception {
        String authType = request.getAuthType();

        boolean requiresAuthCode = "PIN".equals(authType)
                                || "OTP".equals(authType)
                                || "VOICE_OTP".equals(authType);

        if (requiresAuthCode) {
            if (request.getAuthCode() == null || request.getAuthCode().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "errorCode", "ERR_VALIDATION_FAILED",
                    "message", "Dữ liệu đầu vào không hợp lệ!",
                    "details", Map.of("authCode", "Mã xác thực không được để trống")
                ));
            }
        }
 
        if ("FACE_STATIC".equals(authType)) {
            if (request.getFaceImageBase64() == null || request.getFaceImageBase64().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "errorCode", "ERR_VALIDATION_FAILED",
                    "message", "Dữ liệu đầu vào không hợp lệ!",
                    "details", Map.of("faceImageBase64", "Ảnh khuôn mặt tĩnh không được để trống")
                ));
            }
        } else if ("FACE_AI".equals(authType)) {
 
            if (request.getFaceFrameSequence() == null || request.getFaceFrameSequence().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "errorCode", "ERR_VALIDATION_FAILED",
                    "message", "Dữ liệu đầu vào không hợp lệ!",
                    "details", Map.of("faceFrameSequence", "Chuỗi ảnh khuôn mặt AI không được để trống")
                ));
            }
        }

        Transaction tx = transactionRepository.findByIdWithUserSecurity(request.getTransactionId())
                    .orElseThrow(() -> new RuntimeException("Giao dịch không tồn tại!"));

        String username = tx.getFromAccount().getUser().getUsername();
        String currentStatus = tx.getStatus();

 
 
        Set<String> terminalStatuses = Set.of("SUCCESS", "FAILED", "BLOCKED", "REVERSED");
        if (terminalStatuses.contains(currentStatus)) {
            auditLogService.logAction(username, "ILLEGAL_ACCESS", "Cố gắng xác thực giao dịch đã đóng: " + tx.getId());
            return ResponseEntity.status(400).body(Map.of(
                "status", "ERROR", 
                "message", "Giao dịch đã kết thúc (Trạng thái: " + currentStatus + "). Không thể thao tác thêm."
            ));
        }
 
 
        if ("UNDER_REVIEW".equals(currentStatus)) {
            return ResponseEntity.status(403).body(Map.of(
                "status", "FROZEN", 
                "message", "Giao dịch đang được tạm giữ để kiểm duyệt an toàn. Vui lòng chờ hệ thống xử lý."
            ));
        }
        
        if ("PROCESSING".equals(currentStatus)) {
            return ResponseEntity.status(409).body(Map.of(
                "status", "CONFLICT", 
                "message", "Hệ thống đang xử lý trừ tiền, vui lòng không thao tác đúp."
            ));
        }
 
 
        if (currentStatus == null || !currentStatus.startsWith("PENDING_")) {
            return ResponseEntity.status(400).body(Map.of(
                "status", "INVALID_STATE", 
                "message", "Trạng thái giao dịch không hợp lệ để xác thực."
            ));
        }
 

        if ("PIN".equals(authType)) {
            User txUser = tx.getFromAccount().getUser();
            UserSecurity security = txUser.getUserSecurity();
            
            if (security == null) {
                throw new RuntimeException("Hồ sơ bảo mật không tồn tại!");
            }

 
            if ("PENDING_PIN".equals(currentStatus)) { 
 
                pinService.verifyPin(security, request.getAuthCode());

 
                Transaction completedTx = transactionService.executeTransactionCore(tx);
                auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (1 lớp PIN).");
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx));
            } 
            else if ("PENDING_PIN_OTP".equals(currentStatus)) { 
 
                pinService.verifyPin(security, request.getAuthCode());
                
    
                tx.setStatus("PENDING_OTP");
                transactionRepository.save(tx);
 
                otpService.generateAndSendOtpAsync(tx);
                return ResponseEntity.ok(Map.of("status", "NEXT_STEP", "nextAuthType", "OTP", "message", "Mã PIN đúng. Vui lòng nhập OTP vừa được gửi."));
            }
            else if ("PENDING_PIN_FACE".equals(currentStatus)) { 
 
                pinService.verifyPin(security, request.getAuthCode());

 
                tx.setStatus("PENDING_FACE_STATIC");
                transactionRepository.save(tx);
                return ResponseEntity.ok(Map.of("status", "NEXT_STEP", "nextAuthType", "FACE_STATIC", "message", "Mã PIN đúng. Vui lòng quét khuôn mặt bảo mật."));
            }
            else if ("PENDING_PIN_HIGH".equals(currentStatus)) { 
 
                pinService.verifyPin(security, request.getAuthCode());
 
                tx.setStatus("PENDING_ALL_IN_ONE");  
                transactionRepository.save(tx);
                
 
                String voiceCode = otpService.generateVoiceOtp(tx.getId()); 
                
                return ResponseEntity.ok(Map.of(
                        "status", "NEXT_STEP", 
                        "nextAuthType", "ALL_IN_ONE_BIOMETRIC",  
                        "voiceCode", voiceCode,                
                        "message", "Mã PIN đúng. Vui lòng chuẩn bị xác thực sinh trắc học kép."
                ));
            }
            
 
        }

 
         
        else if ("OTP".equals(authType)) {
            boolean isValid = otpService.verifyOtp(tx.getId(), request.getAuthCode());
            if (!isValid) {
                auditLogService.logAction(username, "OTP_FAILED", "Sai OTP giao dịch " + tx.getId());
                return ResponseEntity.badRequest().body("OTP sai hoặc đã hết hạn!");
            }

            if ("PENDING_OTP".equals(currentStatus)) { 
 
                Transaction completedTx = transactionService.executeTransactionCore(tx);
                auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (PIN + OTP).");
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx));
            }
        }

 
        else if ("FACE_STATIC".equals(authType)) {
            if ("PENDING_FACE_STATIC".equals(currentStatus)) {
 
                String savedFaceBase64 = tx.getFromAccount().getUser().getBase64FaceImage();
                if (savedFaceBase64 == null || savedFaceBase64.isEmpty()) {
                    return ResponseEntity.badRequest().body("Lỗi: Người dùng chưa thiết lập FaceID gốc!");
                }
 
                String capturedFaceBase64 = request.getFaceImageBase64();
 
                boolean isMatch = verifyFaceWithAI(savedFaceBase64, capturedFaceBase64);
 
                if (!isMatch) {
 
                    int currentAttempts = tx.getFailedAiAttempts() != null ? tx.getFailedAiAttempts() : 0;
                    currentAttempts++; 
                    tx.setFailedAiAttempts(currentAttempts);

                    if (currentAttempts >= 3) {
 
                        tx.setStatus("BLOCKED");
                        transactionRepository.save(tx);
                        auditLogService.logAction(username, "FACE_REJECT_MAX_RETRIES", "Khóa giao dịch: Xác thực khuôn mặt tĩnh sai quá 3 lần.");
                        return ResponseEntity.status(403).body("Giao dịch bị hủy do xác thực khuôn mặt sai quá 3 lần!");
                    } else {
 
                        transactionRepository.save(tx);
                        int remaining = 3 - currentAttempts;
                        auditLogService.logAction(username, "FACE_REJECT_RETRY", "Quét khuôn mặt sai lần " + currentAttempts);
                        return ResponseEntity.badRequest().body("Khuôn mặt không khớp với cơ sở dữ liệu. Bạn còn " + remaining + " lần thử.");
                    }
                }
 
                tx.setFailedAiAttempts(0);  
                Transaction completedTx = transactionService.executeTransactionCore(tx);
                
                auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (PIN + Khuôn Mặt Tĩnh).");
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx));
            }
        }
 
         
        else if ("FACE_AI".equals(authType)) {
            if ("PENDING_ALL_IN_ONE".equals(currentStatus) || "PENDING_FACE_AI".equals(currentStatus)) {
                boolean isSecure = faceScanActionStrategy.validateFaceAndEmotion(tx, request.getFaceFrameSequence());
                
                if (!isSecure) {
 
                    int currentAttempts = tx.getFailedAiAttempts() != null ? tx.getFailedAiAttempts() : 0;
                    currentAttempts++; 
                    tx.setFailedAiAttempts(currentAttempts);

                    if (currentAttempts >= 3) {
 
                        tx.setStatus("BLOCKED");
                        transactionRepository.save(tx);
                        auditLogService.logAction(username, "AI_REJECT_MAX_RETRIES", "Khóa giao dịch: Xác thực khuôn mặt/cảm xúc sai 3 lần.");
                        return ResponseEntity.status(403).body("Giao dịch bị hủy do xác thực sinh trắc học sai quá 3 lần!");
                    } else {
 
                        transactionRepository.save(tx);
                        int remaining = 3 - currentAttempts;
                        auditLogService.logAction(username, "AI_REJECT_RETRY", "Quét AI sai lần " + currentAttempts);
                        return ResponseEntity.badRequest().body("Khuôn mặt hoặc cảm xúc không khớp. Bạn còn " + remaining + " lần thử.");
                    }
                }
                
 
                tx.setFailedAiAttempts(0);  
                tx.setStatus("PENDING_VOICE_OTP");
                transactionRepository.save(tx);

                return ResponseEntity.ok(Map.of(
                        "status", "NEXT_STEP", 
                        "nextAuthType", "VOICE_OTP", 
                        "message", "Xác thực AI thành công. Vui lòng đọc Voice OTP."
                ));
            }
        }

        return ResponseEntity.badRequest().body("Luồng xác thực bị gián đoạn hoặc không hợp lệ!");
    }





    @GetMapping("/history/{accountId}")
    public ResponseEntity<?> getTransactionHistory(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "ALL") String filter) {
        
        try {
 
            String entryType = null;
            if ("IN".equalsIgnoreCase(filter)) {
                entryType = "CREDIT";
            } else if ("OUT".equalsIgnoreCase(filter)) {
                entryType = "DEBIT";
            }

 
            Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());

 
            org.springframework.data.domain.Page<TransactionLedger> ledgersPage = ledgerRepository.findByAccountIdAndEntryType(accountId, entryType, pageable);
 
            java.util.Set<String> targetAccountNumbers = ledgersPage.getContent().stream()
                    .filter(l -> "DEBIT".equals(l.getEntryType()) && l.getTransaction() != null && l.getTransaction().getToAccountNumber() != null)
                    .map(l -> l.getTransaction().getToAccountNumber())
                    .collect(Collectors.toSet());
 
            Map<String, String> accountNameDictionary = new HashMap<>();
            if (!targetAccountNumbers.isEmpty()) {
                List<Account> targetAccounts = accountRepository.findByAccountNumberIn(targetAccountNumbers);
 
                for (Account acc : targetAccounts) {
                    accountNameDictionary.put(acc.getAccountNumber(), acc.getUser().getFullName());
                }
            }
 
            org.springframework.data.domain.Page<Map<String, Object>> resultPage = ledgersPage.map(l -> {
                Map<String, Object> map = new HashMap<>();
                Transaction rootTx = l.getTransaction();
                
                map.put("id", l.getId());
                map.put("type", l.getEntryType()); 
                map.put("amount", l.getAmount());
                map.put("balanceAfter", l.getBalanceAfter());
                map.put("date", l.getCreatedAt());
                
                String txDescription = (rootTx != null && rootTx.getDescription() != null && !rootTx.getDescription().isEmpty()) 
                        ? rootTx.getDescription() 
                        : (l.getEntryType().equals("DEBIT") ? "Chuyển khoản đi" : "Nhận tiền chuyển khoản");
                map.put("description", txDescription);
                
                if (rootTx != null) {
                    map.put("toAccountNumber", rootTx.getToAccountNumber());
                    map.put("riskLevel", rootTx.getRiskLevel());
                    map.put("totalRiskScore", rootTx.getTotalRiskScore());
                    map.put("emotionSignal", rootTx.getEmotionSignal());

                    String relatedName = "Người dùng ẩn danh";
                    if ("DEBIT".equals(l.getEntryType())) {
 
                        String toAccNum = rootTx.getToAccountNumber();
                        relatedName = accountNameDictionary.getOrDefault(toAccNum, "Người nhận ngoài hệ thống");
                    } else {
 
                        relatedName = rootTx.getFromAccount().getUser().getFullName();
                    }
                    map.put("relatedName", relatedName); 

                } else {
                    map.put("toAccountNumber", "N/A");
                    map.put("riskLevel", "LOW");
                    map.put("totalRiskScore", 0);
                    map.put("emotionSignal", "N/A");
                    map.put("relatedName", "Hệ thống FinRisk");
                }

                return map;
            });
            
            return ResponseEntity.ok(resultPage);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi lấy lịch sử: " + e.getMessage());
        }
    }

    @GetMapping("/lookup/{accountNumber}")
    public ResponseEntity<?> lookupAccountName(@PathVariable String accountNumber) {
        try {
            Account account = accountRepository.findByAccountNumberWithUser(accountNumber)
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
 
            List<TransactionLedger> recentLedgers = ledgerRepository.findRecentDebitsWithTransaction(accountId, sevenDaysAgo);
 
            List<String> targetAccountNumbers = recentLedgers.stream()
                .map(l -> l.getTransaction().getToAccountNumber())
                .filter(accNum -> accNum != null)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
 
            Map<String, String> accountNameDictionary = new HashMap<>();
            if (!targetAccountNumbers.isEmpty()) {
 
                List<Account> targetAccounts = accountRepository.findByAccountNumberIn(new java.util.HashSet<>(targetAccountNumbers));
                for (Account acc : targetAccounts) {
                    accountNameDictionary.put(acc.getAccountNumber(), acc.getUser().getFullName());
                }
            }

 
            List<Map<String, String>> recipients = targetAccountNumbers.stream().map(accNum -> {
                Map<String, String> map = new HashMap<>();
                map.put("accountNumber", accNum);
 
                map.put("fullName", accountNameDictionary.getOrDefault(accNum, "Người nhận ngoài hệ thống"));
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(recipients);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi lấy danh sách gần đây: " + e.getMessage());
        }
    }
 
    @GetMapping("/analytics/{accountId}")
    public ResponseEntity<?> get7DaysAnalytics(@PathVariable Long accountId) {
        try {
 
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7).withHour(0).withMinute(0).withSecond(0);
 
            List<TransactionLedger> rawLedgers = ledgerRepository.findTransactionsForAnalytics(accountId, sevenDaysAgo);

 
            java.util.Set<String> targetAccountNumbers = rawLedgers.stream()
                    .filter(l -> "DEBIT".equals(l.getEntryType()) && l.getTransaction() != null && l.getTransaction().getToAccountNumber() != null)
                    .map(l -> l.getTransaction().getToAccountNumber())
                    .collect(Collectors.toSet());

            Map<String, String> accountNameDictionary = new HashMap<>();
            if (!targetAccountNumbers.isEmpty()) {
                List<Account> targetAccounts = accountRepository.findByAccountNumberIn(targetAccountNumbers);
                for (Account acc : targetAccounts) {
                    accountNameDictionary.put(acc.getAccountNumber(), acc.getUser().getFullName());
                }
            }
 
            List<Map<String, Object>> result = rawLedgers.stream().map(l -> {
                Map<String, Object> map = new HashMap<>();
                Transaction rootTx = l.getTransaction();
                
                map.put("id", l.getId());
                map.put("type", l.getEntryType()); 
                map.put("amount", l.getAmount());
                map.put("balanceAfter", l.getBalanceAfter());
                map.put("date", l.getCreatedAt());  
                
                String txDescription = (rootTx != null && rootTx.getDescription() != null && !rootTx.getDescription().isEmpty()) 
                        ? rootTx.getDescription() 
                        : (l.getEntryType().equals("DEBIT") ? "Chuyển khoản đi" : "Nhận tiền chuyển khoản");
                map.put("description", txDescription);
                
                if (rootTx != null) {
                    map.put("toAccountNumber", rootTx.getToAccountNumber());
                    map.put("riskLevel", rootTx.getRiskLevel());
                    map.put("totalRiskScore", rootTx.getTotalRiskScore());
                    
                    String relatedName = "Người dùng ẩn danh";
                    if ("DEBIT".equals(l.getEntryType())) {
                        relatedName = accountNameDictionary.getOrDefault(rootTx.getToAccountNumber(), "Người nhận ngoài hệ thống");
                    } else {
                        relatedName = rootTx.getFromAccount().getUser().getFullName();
                    }
                    map.put("relatedName", relatedName); 
                }
                
                return map;
            }).collect(Collectors.toList());

 
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi lấy dữ liệu Analytics: " + e.getMessage());
        }
    }

private boolean verifyFaceWithAI(String savedFaceBase64, String capturedFaceBase64) {
    try {
 
        String URL_AI_SERVER = "http://localhost:5000/api/ai/verify-face"; 

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
 
        Map<String, String> body = new HashMap<>();
        body.put("live_image_base64", capturedFaceBase64);      
        body.put("registered_image_base64", savedFaceBase64);  

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
 
        ResponseEntity<FaceAIResponse> response = restTemplate.postForEntity(
                URL_AI_SERVER, request, FaceAIResponse.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            boolean isMatched = response.getBody().isMatched();
            log.info("🤖 AI Chốt hạ: {}", isMatched ? "KHỚP MẶT ✅" : "SAI MẶT ❌");
            return isMatched;
        }
        return false;
    } catch (Exception e) {
        log.error("❌ KHÔNG KẾT NỐI ĐƯỢC AI (Port 5000): {}. Hãy chắc chắn đã chạy file Python!", e.getMessage());
 
        return false; 
    }
}
}