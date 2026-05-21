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

    // //Transaction B1: Nhan yêu cầu khởi tạo giao dịch -> Transaction B2: Gọi AccountRepository 
    // @PostMapping("/process")

    // public ResponseEntity<?> processTransaction(@Valid @RequestBody TransactionRequest request, HttpServletRequest httpRequest) throws Exception {
        
    //     String currentIp = httpRequest.getRemoteAddr();
    //     String currentDevice = httpRequest.getHeader("User-Agent");
        
    //     if (currentDevice != null && currentDevice.length() > 250) {
    //         currentDevice = currentDevice.substring(0, 250);
    //     }
 
    //     Transaction result = transactionService.initiateTransaction(
    //             request.getFromAccountId(),
    //             request.getToAccount(),
    //             request.getAmount(),
    //             request.getDescription(),
    //             currentIp,      
    //             currentDevice   
    //     );

    //     String username = result.getFromAccount().getUser().getUsername();
    //     auditLogService.logAction(username, "TRANSACTION_INITIATED", "Tạo lệnh chuyển " + request.getAmount() + " VND đến STK " + request.getToAccount() + ". Mức rủi ro: " + result.getRiskLevel());

    //     return ResponseEntity.ok(result);
    // }

    //Transaction B1: Nhan yêu cầu khởi tạo giao dịch -> Transaction B2: Gọi AccountRepository 
    @PostMapping("/process")
    public ResponseEntity<?> processTransaction(@Valid @RequestBody TransactionRequest request, HttpServletRequest httpRequest) throws Exception {
        
        String currentIp = httpRequest.getRemoteAddr();
        String currentDevice = httpRequest.getHeader("User-Agent");
        
        if (currentDevice != null && currentDevice.length() > 250) {
            currentDevice = currentDevice.substring(0, 250);
        }
 
        // 1. Khởi tạo giao dịch qua Service như bình thường
        Transaction result = transactionService.initiateTransaction(
                request.getFromAccountId(),
                request.getToAccount(),
                request.getAmount(),
                request.getDescription(),
                currentIp,      
                currentDevice   
        );

        // 🚀 BƯỚC NGOẶT: Can thiệp thời gian bối cảnh phục vụ Test Case linh hoạt
        // Nếu trong Body JSON từ Postman có truyền "createdAt", ta ghi đè mốc thời gian này vào Entity
        if (request.getCreatedAt() != null) {
            result.setCreatedAt(request.getCreatedAt());
            // Tiến hành đồng bộ lưu đè mốc thời gian mới sửa xuống Database luôn
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

// 🚀 TÁCH RIÊNG VALIDATION CHO TỪNG LOẠI AI
        if ("FACE_STATIC".equals(authType)) {
            if (request.getFaceImageBase64() == null || request.getFaceImageBase64().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "errorCode", "ERR_VALIDATION_FAILED",
                    "message", "Dữ liệu đầu vào không hợp lệ!",
                    "details", Map.of("faceImageBase64", "Ảnh khuôn mặt tĩnh không được để trống")
                ));
            }
        } else if ("FACE_AI".equals(authType)) {
            // Kiểm tra xem Mảng gửi lên có bị null hoặc rỗng không
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

 
        // 1: NHÓM TERMINAL (CHỐT SỔ) - TRẠNG THÁI TỬ
        // Tuyệt đối không cho phép thao tác lại, tránh Double-Spend hoặc Replay Attack
 
        Set<String> terminalStatuses = Set.of("SUCCESS", "FAILED", "BLOCKED", "REVERSED");
        if (terminalStatuses.contains(currentStatus)) {
            auditLogService.logAction(username, "ILLEGAL_ACCESS", "Cố gắng xác thực giao dịch đã đóng: " + tx.getId());
            return ResponseEntity.status(400).body(Map.of(
                "status", "ERROR", 
                "message", "Giao dịch đã kết thúc (Trạng thái: " + currentStatus + "). Không thể thao tác thêm."
            ));
        }

        //  2: NHÓM SUSPENDED (ĐÓNG BĂNG / CHỜ XỬ LÝ)
        // Bắt user phải chờ đợi, chặn mọi nỗ lực bấm nút liên tục
 
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

        //  CỬA KHẨU 3: NHÓM ACTIVE (ĐANG SỐNG)
        // Đảm bảo chỉ có các trạng thái PENDING_... mới được lọt xuống logic xác thực
 
        if (currentStatus == null || !currentStatus.startsWith("PENDING_")) {
            return ResponseEntity.status(400).body(Map.of(
                "status", "INVALID_STATE", 
                "message", "Trạng thái giao dịch không hợp lệ để xác thực."
            ));
        }

        //   NẾU QUA ĐƯỢC 3 CỬA KHẨU TRÊN -> VÀO LOGIC XÁC THỰC CỦA BRO

        if ("PIN".equals(authType)) {
            User txUser = tx.getFromAccount().getUser();
            UserSecurity security = txUser.getUserSecurity();
            
            if (security == null) {
                throw new RuntimeException("Hồ sơ bảo mật không tồn tại!");
            }

            // ĐIỀU HƯỚNG TẠI ĐÂY (BẺ GHI)
            if ("PENDING_PIN".equals(currentStatus)) { 
                //   DỜI HÀM CHECK PIN VÀO TRONG NÀY
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

                // 🚀 LUỒNG HIGH MỚI: ATOMIC VERIFICATION (3 TRONG 1)
                tx.setStatus("PENDING_ALL_IN_ONE"); // Đổi trạng thái để đón luồng gộp
                transactionRepository.save(tx);
                
                // 🚀 BƯỚC NGOẶT: SINH MÃ VOICE OTP NGAY TẠI TRẠM NÀY!
                String voiceCode = otpService.generateVoiceOtp(tx.getId()); 
                
                return ResponseEntity.ok(Map.of(
                        "status", "NEXT_STEP", 
                        "nextAuthType", "ALL_IN_ONE_BIOMETRIC", // Báo cho React bật giao diện mới
                        "voiceCode", voiceCode,                 // Gửi mã OTP đạn dược lên thẳng React
                        "message", "Mã PIN đúng. Vui lòng chuẩn bị xác thực sinh trắc học kép."
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
         
// TRẠM 2: QUÉT MẶT TĨNH (DÀNH RIÊNG CHO MEDIUM_2)
        else if ("FACE_STATIC".equals(authType)) {
            if ("PENDING_FACE_STATIC".equals(currentStatus)) {
                
                // 1. Rút ảnh gốc từ Database ra
                String savedFaceBase64 = tx.getFromAccount().getUser().getBase64FaceImage();
                if (savedFaceBase64 == null || savedFaceBase64.isEmpty()) {
                    return ResponseEntity.badRequest().body("Lỗi: Người dùng chưa thiết lập FaceID gốc!");
                }

                // 2. Lấy ảnh Webcam Frontend gửi lên
                String capturedFaceBase64 = request.getFaceImageBase64();

                // 3. 🚀 GỌI HÀM AI SO SÁNH KHUÔN MẶT
                boolean isMatch = verifyFaceWithAI(savedFaceBase64, capturedFaceBase64);

                // 4. XỬ LÝ KẾT QUẢ TỪ AI
                if (!isMatch) {
                    // Lôi biến đếm ra (đề phòng null thì gán = 0)
                    int currentAttempts = tx.getFailedAiAttempts() != null ? tx.getFailedAiAttempts() : 0;
                    currentAttempts++; 
                    tx.setFailedAiAttempts(currentAttempts);

                    if (currentAttempts >= 3) {
                        // ❌ SAI 3 LẦN -> KHÓA GIAO DỊCH
                        tx.setStatus("BLOCKED");
                        transactionRepository.save(tx);
                        auditLogService.logAction(username, "FACE_REJECT_MAX_RETRIES", "Khóa giao dịch: Xác thực khuôn mặt tĩnh sai quá 3 lần.");
                        return ResponseEntity.status(403).body("Giao dịch bị hủy do xác thực khuôn mặt sai quá 3 lần!");
                    } else {
                        // ⚠️ SAI DƯỚI 3 LẦN -> BÁO LỖI VÀ TRỪ SỐ LẦN THỬ
                        transactionRepository.save(tx);
                        int remaining = 3 - currentAttempts;
                        auditLogService.logAction(username, "FACE_REJECT_RETRY", "Quét khuôn mặt sai lần " + currentAttempts);
                        return ResponseEntity.badRequest().body("Khuôn mặt không khớp với cơ sở dữ liệu. Bạn còn " + remaining + " lần thử.");
                    }
                }

                // 5. ✅ NẾU KHUÔN MẶT KHỚP -> CHỐT ĐƠN TRỪ TIỀN!
                tx.setFailedAiAttempts(0); // Reset bộ đếm
                Transaction completedTx = transactionService.executeTransactionCore(tx);
                
                auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (PIN + Khuôn Mặt Tĩnh).");
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx));
            }
        }


         
        // TRẠM 3: QUÉT MẶT AI + CẢM XÚC 
         
        else if ("FACE_AI".equals(authType)) {
            if ("PENDING_ALL_IN_ONE".equals(currentStatus) || "PENDING_FACE_AI".equals(currentStatus)) {
                boolean isSecure = faceScanActionStrategy.validateFaceAndEmotion(tx, request.getFaceFrameSequence());
                
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
 // =========================================================================
    // 🚀 API MỚI DÀNH RIÊNG CHO MÀN HÌNH BIỂU ĐỒ 7 NGÀY (Không phân trang)
    // =========================================================================
    @GetMapping("/analytics/{accountId}")
    public ResponseEntity<?> get7DaysAnalytics(@PathVariable Long accountId) {
        try {
            // 1. Tính toán ngày bắt đầu (7 ngày trước, tính từ 00:00:00)
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7).withHour(0).withMinute(0).withSecond(0);

            // 2. Kéo MỘT MẺ toàn bộ giao dịch từ Repository
            List<TransactionLedger> rawLedgers = ledgerRepository.findTransactionsForAnalytics(accountId, sevenDaysAgo);

            // 3. Gom mẻ Account để lấy tên (Batch Fetching - Giống hàm History)
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

            // 4. Map dữ liệu sang JSON cho Frontend dễ xơi
            List<Map<String, Object>> result = rawLedgers.stream().map(l -> {
                Map<String, Object> map = new HashMap<>();
                Transaction rootTx = l.getTransaction();
                
                map.put("id", l.getId());
                map.put("type", l.getEntryType()); 
                map.put("amount", l.getAmount());
                map.put("balanceAfter", l.getBalanceAfter());
                map.put("date", l.getCreatedAt()); // Giữ nguyên tên là 'date' theo chuẩn cũ của bro
                
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

            // 5. Trả về đúng một MẢNG (List), không có vỏ bọc 'content' hay 'pageable'
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi lấy dữ liệu Analytics: " + e.getMessage());
        }
    }

private boolean verifyFaceWithAI(String savedFaceBase64, String capturedFaceBase64) {
    try {
        // 🚀 BƯỚC 1: Sửa đúng Port 5000 và đúng Path của con Python
        String URL_AI_SERVER = "http://localhost:5000/api/ai/verify-face"; 

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 🚀 BƯỚC 2: Sửa đúng KEY mà code Python (FastAPI) đang yêu cầu
        Map<String, String> body = new HashMap<>();
        body.put("live_image_base64", capturedFaceBase64);      // Python cần live_image_base64
        body.put("registered_image_base64", savedFaceBase64); // Python cần registered_image_base64

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        // 🚀 BƯỚC 3: Gửi đi
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
        // Trả về false để hệ thống chặn giao dịch khi AI "sập"
        return false; 
    }
}
}