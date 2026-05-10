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

        boolean requiresFaceImage = "FACE_STATIC".equals(authType)
                                || "FACE_AI".equals(authType);

        if (requiresFaceImage) {
            if (request.getFaceImageBase64() == null || request.getFaceImageBase64().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "errorCode", "ERR_VALIDATION_FAILED",
                    "message", "Dữ liệu đầu vào không hợp lệ!",
                    "details", Map.of("faceImageBase64", "Ảnh khuôn mặt không được để trống")
                ));
            }
        }

        Transaction tx = transactionRepository.findByIdWithUserSecurity(request.getTransactionId())
                    .orElseThrow(() -> new RuntimeException("Giao dịch không tồn tại!"));

        String username = tx.getFromAccount().getUser().getUsername();
        String currentStatus = tx.getStatus();

        if ("PIN".equals(authType)) {
            User txUser = tx.getFromAccount().getUser();
            UserSecurity security = txUser.getUserSecurity();
            
            if (security == null) {
                throw new RuntimeException("Hồ sơ bảo mật không tồn tại!");
            }

            // ĐIỀU HƯỚNG TẠI ĐÂY (BẺ GHI)
            if ("PENDING_PIN".equals(currentStatus)) { 
                // 🚀 DỜI HÀM CHECK PIN VÀO TRONG NÀY
                pinService.verifyPin(security, request.getAuthCode());

                //  LUỒNG LOW: CHỐT LUÔN!
                Transaction completedTx = transactionService.executeTransactionCore(tx);
                auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (1 lớp PIN).");
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx));
            } 
            else if ("PENDING_PIN_OTP".equals(currentStatus)) { 
                // 🚀 DỜI HÀM CHECK PIN VÀO TRONG NÀY
                pinService.verifyPin(security, request.getAuthCode());
                
                //  LUỒNG MEDIUM_1: SANG TRẠM OTP
                tx.setStatus("PENDING_OTP");
                transactionRepository.save(tx);
                //  BÂY GIỜ MỚI GỌI HÀM SINH OTP ĐỂ GỬI ĐI!
                otpService.generateAndSendOtpAsync(tx);
                return ResponseEntity.ok(Map.of("status", "NEXT_STEP", "nextAuthType", "OTP", "message", "Mã PIN đúng. Vui lòng nhập OTP vừa được gửi."));
            }
            else if ("PENDING_PIN_FACE".equals(currentStatus)) { 
                // 🚀 DỜI HÀM CHECK PIN VÀO TRONG NÀY
                pinService.verifyPin(security, request.getAuthCode());

                //  LUỒNG MEDIUM_2: BỎ QUA OTP, ĐÁ THẲNG SANG QUÉT MẶT
                tx.setStatus("PENDING_FACE_STATIC");
                transactionRepository.save(tx);
                return ResponseEntity.ok(Map.of("status", "NEXT_STEP", "nextAuthType", "FACE_STATIC", "message", "Mã PIN đúng. Vui lòng quét khuôn mặt bảo mật."));
            }
            else if ("PENDING_PIN_HIGH".equals(currentStatus)) { 
                // 🚀 DỜI HÀM CHECK PIN VÀO TRONG NÀY
                pinService.verifyPin(security, request.getAuthCode());

                //  LUỒNG HIGH: SANG TRẠM QUÉT MẶT TRƯỚC!
                tx.setStatus("PENDING_FACE_AI");
                transactionRepository.save(tx);
                return ResponseEntity.ok(Map.of(
                        "status", "NEXT_STEP", 
                        "nextAuthType", "FACE_AI", 
                        "message", "Mã PIN đúng. Vui lòng quét khuôn mặt bảo mật."
                ));
            }
            
            // 🚀 NẾU RỚT XUỐNG ĐÂY (VÍ DỤ STATUS LÀ "BLOCKED", "SUCCESS" hay "OTP") 
            // THÌ SẼ KHÔNG CÓ HÀM PIN NÀO ĐƯỢC GỌI, CHẠY TUỘT XUỐNG ĐÁY CONTROLLER ĐỂ BÁO LỖI 400!
        }

         
        // TRẠM 1: XÁC THỰC OTP (DÀNH RIÊNG CHO MEDIUM_1)
         
        else if ("OTP".equals(authType)) {
            boolean isValid = otpService.verifyOtp(tx.getId(), request.getAuthCode());
            if (!isValid) {
                auditLogService.logAction(username, "OTP_FAILED", "Sai OTP giao dịch " + tx.getId());
                return ResponseEntity.badRequest().body("OTP sai hoặc đã hết hạn!");
            }

            if ("PENDING_OTP".equals(currentStatus)) { 
                // 🟡 LUỒNG MEDIUM_1: CHỐT TẠI ĐÂY!
                Transaction completedTx = transactionService.executeTransactionCore(tx);
                auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (PIN + OTP).");
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx));
            }
        }

         
        // TRẠM 2: QUÉT MẶT TĨNH (DÀNH RIÊNG CHO MEDIUM_2)
         
        else if ("FACE_STATIC".equals(authType)) {
            if ("PENDING_FACE_STATIC".equals(currentStatus)) {
                // LUỒNG MEDIUM_2: CHỐT TẠI ĐÂY!
                // TODO: Chỗ này bro gắn cái hàm AI FaceMatch sau nhé
                Transaction completedTx = transactionService.executeTransactionCore(tx);
                auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (PIN + Khuôn Mặt).");
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx));
            }
        }


         
        // TRẠM 3: QUÉT MẶT AI + CẢM XÚC 
         
        else if ("FACE_AI".equals(authType)) {
            if ("PENDING_FACE_AI".equals(currentStatus)) {
                boolean isSecure = faceScanActionStrategy.validateFaceAndEmotion(tx, request.getFaceImageBase64());
                
                if (!isSecure) {
                    // Lôi biến đếm ra (đề phòng null thì gán = 0)
                    int currentAttempts = tx.getFailedAiAttempts() != null ? tx.getFailedAiAttempts() : 0;
                    currentAttempts++; // Tăng lên 1
                    tx.setFailedAiAttempts(currentAttempts);

                    if (currentAttempts >= 3) {
                        //  SAI QUÁ 3 LẦN: CHỐT BLOCKED
                        tx.setStatus("BLOCKED");
                        transactionRepository.save(tx);
                        auditLogService.logAction(username, "AI_REJECT_MAX_RETRIES", "Khóa giao dịch: Xác thực khuôn mặt/cảm xúc sai 3 lần.");
                        return ResponseEntity.status(403).body("Giao dịch bị hủy do xác thực sinh trắc học sai quá 3 lần!");
                    } else {
                        //  VẪN CÒN CƠ HỘI: LƯU BIẾN ĐẾM VÀ TRẢ VỀ LỖI 400
                        transactionRepository.save(tx);
                        int remaining = 3 - currentAttempts;
                        auditLogService.logAction(username, "AI_REJECT_RETRY", "Quét AI sai lần " + currentAttempts);
                        return ResponseEntity.badRequest().body("Khuôn mặt hoặc cảm xúc không khớp. Bạn còn " + remaining + " lần thử.");
                    }
                }
                
                //  NẾU QUÉT THÀNH CÔNG: Chuyển sang trạm Voice OTP
                tx.setFailedAiAttempts(0); // Reset bộ đếm cho sạch sẽ
                tx.setStatus("PENDING_VOICE_OTP");
                transactionRepository.save(tx);
                
                String voiceCode = otpService.generateVoiceOtp(tx.getId()); 
                
                return ResponseEntity.ok(Map.of(
                        "status", "NEXT_STEP", 
                        "nextAuthType", "VOICE_OTP", 
                        "voiceCode", voiceCode,
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
            // 1. Dịch thuật Filter
            String entryType = null;
            if ("IN".equalsIgnoreCase(filter)) {
                entryType = "CREDIT";
            } else if ("OUT".equalsIgnoreCase(filter)) {
                entryType = "DEBIT";
            }

            // 2. Cấu hình Phân trang
            Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());

            // 3. Lấy dữ liệu Sổ cái (Đã JOIN sẵn Transaction và Account gửi)
            org.springframework.data.domain.Page<TransactionLedger> ledgersPage = ledgerRepository.findByAccountIdAndEntryType(accountId, entryType, pageable);

            // =========================================================
            // 🚀 BẮT ĐẦU THUẬT TOÁN GOM MẺ (BATCH FETCHING)
            // =========================================================
            
            // Bước A: Gom tất cả các STK nhận tiền vào 1 cái rổ (Set để lọc trùng)
            java.util.Set<String> targetAccountNumbers = ledgersPage.getContent().stream()
                    .filter(l -> "DEBIT".equals(l.getEntryType()) && l.getTransaction() != null && l.getTransaction().getToAccountNumber() != null)
                    .map(l -> l.getTransaction().getToAccountNumber())
                    .collect(Collectors.toSet());

            // Bước B: Chọc DB ĐÚNG 1 LẦN để lấy toàn bộ thông tin các Account đó
            Map<String, String> accountNameDictionary = new HashMap<>();
            if (!targetAccountNumbers.isEmpty()) {
                List<Account> targetAccounts = accountRepository.findByAccountNumberIn(targetAccountNumbers);
                // Tạo cuốn từ điển trên RAM: Key là STK, Value là Tên
                for (Account acc : targetAccounts) {
                    accountNameDictionary.put(acc.getAccountNumber(), acc.getUser().getFullName());
                }
            }
            // =========================================================

            // 4. Biến hóa dữ liệu (Nhào nặn JSON)
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
                        // 🚀 THAY VÌ GỌI DB NHƯ CŨ, GIỜ CHỈ VIỆC TRA TỪ ĐIỂN TRÊN RAM (Tốc độ ánh sáng)
                        String toAccNum = rootTx.getToAccountNumber();
                        relatedName = accountNameDictionary.getOrDefault(toAccNum, "Người nhận ngoài hệ thống");
                    } else {
                        // Nếu là tiền vào (CREDIT) thì tên người gửi đã được JOIN FETCH kéo về sẵn rồi
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
            
            // 1. Chọc DB bằng vũ khí mới: Lấy sẵn Sổ cái + Transaction (Lọc sẵn DEBIT và 7 ngày)
            List<TransactionLedger> recentLedgers = ledgerRepository.findRecentDebitsWithTransaction(accountId, sevenDaysAgo);
            
            // 2. Thu thập STK: Rút gọn mảng, gạt bỏ trùng lặp (distinct) và chốt lấy đúng 5 STK mới nhất
            List<String> targetAccountNumbers = recentLedgers.stream()
                .map(l -> l.getTransaction().getToAccountNumber())
                .filter(accNum -> accNum != null)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());

            // 3. Gom mẻ DB (Batch Fetching) & Lập từ điển trên RAM
            Map<String, String> accountNameDictionary = new HashMap<>();
            if (!targetAccountNumbers.isEmpty()) {
                // Nhét list 5 số tài khoản vào DB để tra 1 lần duy nhất
                List<Account> targetAccounts = accountRepository.findByAccountNumberIn(new java.util.HashSet<>(targetAccountNumbers));
                for (Account acc : targetAccounts) {
                    accountNameDictionary.put(acc.getAccountNumber(), acc.getUser().getFullName());
                }
            }

            // 4. Lắp ráp: Lôi 5 cái STK ra, tra từ điển lấy tên rồi đóng gói gửi về React
            List<Map<String, String>> recipients = targetAccountNumbers.stream().map(accNum -> {
                Map<String, String> map = new HashMap<>();
                map.put("accountNumber", accNum);
                // Tìm thấy thì lấy tên, không thì báo "Người nhận ngoài hệ thống" (Tốc độ 0.001ms)
                map.put("fullName", accountNameDictionary.getOrDefault(accNum, "Người nhận ngoài hệ thống"));
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(recipients);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi lấy danh sách gần đây: " + e.getMessage());
        }
    }

    // API MỚI: CHỐT SỔ CHO LUỒNG HIGH RISK (TRỪ TIỀN)
    @PostMapping(value = "/verify-voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> verifyVoiceLiveness(
            @RequestParam("transactionId") Long transactionId,
            @RequestParam("audioFile") org.springframework.web.multipart.MultipartFile audioFile) {
        try {
            // 🚀 BƯỚC NGOẶT: Dùng hàm JOIN FETCH siêu to khổng lồ thay cho findById mặc định
            Transaction tx = transactionRepository.findByIdWithUserSecurity(transactionId)
                    .orElseThrow(() -> new RuntimeException("Giao dịch không tồn tại!"));

            // 🚀 Nhờ hàm trên, dòng này bây giờ tốn 0 query (Lấy thẳng từ RAM)
            String username = tx.getFromAccount().getUser().getUsername();

            if (!"PENDING_VOICE_OTP".equals(tx.getStatus())) {
                return ResponseEntity.badRequest().body("Trạng thái giao dịch không hợp lệ!");
            }

            // Gửi Audio sang Python để bóc băng lấy chữ số
            String recognizedCode = riskEvaluationService.verifyVoiceLivenessAsync(audioFile).get();

            if (recognizedCode == null || recognizedCode.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Không thể nhận diện giọng nói, vui lòng thử lại ở nơi yên tĩnh!");
            }

            // Đem kết quả Python so với OTP trong Redis
            boolean isValid = otpService.verifyOtp(tx.getId(), recognizedCode);

            if (!isValid) {
                auditLogService.logAction(username, "VOICE_FAILED", "Đọc sai Voice OTP giao dịch " + tx.getId());
                return ResponseEntity.badRequest().body("Mã giọng nói không khớp ("+ recognizedCode +"). Yêu cầu đọc to, rõ ràng!");
            }

            // 🚀 FIX LỖI: VƯỢT QUA VOICE LÀ CHỐT SỔ TRỪ TIỀN LUÔN! KHÔNG ĐÁ ĐI ĐÂU NỮA
            Transaction completedTx = transactionService.executeTransactionCore(tx);
            auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (PIN + Face AI + Voice Liveness).");
            
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi xử lý âm thanh: " + e.getMessage());
        }
    }
}