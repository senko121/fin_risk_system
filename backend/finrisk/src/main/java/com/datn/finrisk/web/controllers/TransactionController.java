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

import com.datn.finrisk.core.services.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
 import org.springframework.data.domain.Page;
 import org.springframework.data.domain.PageRequest;
 import org.springframework.data.domain.Pageable;
 import org.springframework.data.domain.Sort;
 import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import java.util.UUID; 
import com.datn.finrisk.core.entities.BiometricSession;
import com.datn.finrisk.core.repository.BiometricSessionRepository;
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
import java.util.Optional;
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

    @Autowired
    private BiometricSessionRepository biometricSessionRepository;

    @Value("${ai.timeout.parallel-seconds:20}")
    private int aiParallelTimeoutSeconds;

    //Transaction B1: Nhan yêu cầu khởi tạo giao dịch -> Transaction B2: Gọi AccountRepository
    @PostMapping("/process")
    public ResponseEntity<?> processTransaction(@Valid @RequestBody TransactionRequest request, HttpServletRequest httpRequest) throws Exception {
        
        String currentIp = httpRequest.getRemoteAddr();
        String currentDevice = httpRequest.getHeader("User-Agent");
        
        if (currentDevice != null && currentDevice.length() > 250) {
            currentDevice = currentDevice.substring(0, 250);
        }

        String principalUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        Account senderAccount = accountRepository.findByIdWithUserAndSecurity(request.getFromAccountId()).orElse(null);
        if (senderAccount == null || !senderAccount.getUser().getUsername().equals(principalUsername)) {
            return ResponseEntity.status(403).body(Map.of(
                "status", "FORBIDDEN",
                "message", "Không có quyền thực hiện giao dịch từ tài khoản này."
            ));
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
    public CompletableFuture<ResponseEntity<?>> verifyAndExecute(@Valid @RequestBody AuthVerifyRequest request) throws Exception {
        log.info("🚨 [BẪY DEBUG] Nhận request verify cho TxID: {} - Lúc: {}", request.getTransactionId(), System.currentTimeMillis());
        Optional<ResponseEntity<?>> inputError = validateVerifyInput(request);
        if (inputError.isPresent()) {
            return CompletableFuture.completedFuture(inputError.get());
        }

        Transaction tx = transactionRepository.findByIdWithUserSecurity(request.getTransactionId())
                .orElseThrow(() -> new RuntimeException("Giao dịch không tồn tại!"));

        String username = tx.getFromAccount().getUser().getUsername();
        String principalUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!username.equals(principalUsername)) {
            return CompletableFuture.completedFuture(ResponseEntity.status(403).body(Map.of(
                "status", "FORBIDDEN",
                "message", "Không có quyền xác thực giao dịch này."
            )));
        }

        Optional<ResponseEntity<?>> statusError = guardTransactionStatus(tx, username);
        if (statusError.isPresent()) {
            return CompletableFuture.completedFuture(statusError.get());
        }

        return switch (request.getAuthType()) {
            case "PIN"         -> handlePin(tx, username, request);
            case "OTP"         -> handleOtp(tx, username, request);
            case "FACE_STATIC" -> handleFaceStatic(tx, username, request);
            case "FACE_AI"     -> handleFaceAi(tx, username, request);
            default            -> CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body("Luồng xác thực bị gián đoạn hoặc không hợp lệ!"));
        };
    }

    private Optional<ResponseEntity<?>> validateVerifyInput(AuthVerifyRequest request) {
        String authType = request.getAuthType();
        boolean requiresAuthCode = "PIN".equals(authType) || "OTP".equals(authType) || "VOICE_OTP".equals(authType);
        if (requiresAuthCode && (request.getAuthCode() == null || request.getAuthCode().trim().isEmpty())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(
                "errorCode", "ERR_VALIDATION_FAILED",
                "message", "Dữ liệu đầu vào không hợp lệ!",
                "details", Map.of("authCode", "Mã xác thực không được để trống")
            )));
        }
        if ("FACE_STATIC".equals(authType) && (request.getFaceImageBase64() == null || request.getFaceImageBase64().trim().isEmpty())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(
                "errorCode", "ERR_VALIDATION_FAILED",
                "message", "Dữ liệu đầu vào không hợp lệ!",
                "details", Map.of("faceImageBase64", "Ảnh khuôn mặt tĩnh không được để trống")
            )));
        }
        if ("FACE_AI".equals(authType) && (request.getFaceFrameSequence() == null || request.getFaceFrameSequence().isEmpty())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(
                "errorCode", "ERR_VALIDATION_FAILED",
                "message", "Dữ liệu đầu vào không hợp lệ!",
                "details", Map.of("faceFrameSequence", "Chuỗi ảnh khuôn mặt AI không được để trống")
            )));
        }
        return Optional.empty();
    }

private Optional<ResponseEntity<?>> guardTransactionStatus(Transaction tx, String username) {
        String status = tx.getStatus();
        
        // 🚀 ĐÃ VÁ: Chuyển tất cả lỗi trạng thái về HTTP 400 (Bad Request) để Spring Security không đánh tráo thành 401
        if (Set.of("SUCCESS", "FAILED", "BLOCKED", "REVERSED").contains(status)) {
            log.warn("⚠️ [SECURITY-GUARD] Từ chối xử lý. Giao dịch {} đã đóng với trạng thái: {}", tx.getId(), status);
            auditLogService.logAction(username, "ILLEGAL_ACCESS", "Cố gắng xác thực giao dịch đã đóng: " + tx.getId());
            return Optional.of(ResponseEntity.badRequest().body(Map.of(
                "status", "ALREADY_PROCESSED",
                "errorCode", "ERR_TRANSACTION_CLOSED",
                "message", "Giao dịch này đã kết thúc xử lý thành công trước đó (Trạng thái: " + status + ")."
            )));
        }
        
        if ("UNDER_REVIEW".equals(status)) {
            log.info("🛡️ [SECURITY-GUARD] Giao dịch {} đang ở trạng thái đóng băng để kiểm duyệt.", tx.getId());
            return Optional.of(ResponseEntity.badRequest().body(Map.of(
                "status", "FROZEN",
                "errorCode", "ERR_TRANSACTION_FROZEN",
                "message", "Giao dịch đang được tạm giữ để kiểm duyệt an toàn. Vui lòng chờ hệ thống xử lý."
            )));
        }
        
        if ("PROCESSING".equals(status)) {
            log.warn("⚠️ [SECURITY-GUARD] Phát hiện thao tác đúp. Giao dịch {} đang trừ tiền nền.", tx.getId());
            return Optional.of(ResponseEntity.badRequest().body(Map.of(
                "status", "CONFLICT",
                "errorCode", "ERR_TRANSACTION_PROCESSING",
                "message", "Hệ thống đang xử lý trừ tiền, vui lòng không thao tác đúp."
            )));
        }
        
        if (status == null || !status.startsWith("PENDING_")) {
            log.error("❌ [SECURITY-GUARD] Trạng thái không hợp lệ txId={} status={}", tx.getId(), status);
            return Optional.of(ResponseEntity.badRequest().body(Map.of(
                "status", "INVALID_STATE",
                "errorCode", "ERR_INVALID_STATE",
                "message", "Trạng thái giao dịch không hợp lệ để thực hiện xác thực PIN."
            )));
        }
        
        return Optional.empty();
    }

private CompletableFuture<ResponseEntity<?>> handlePin(Transaction tx, String username, AuthVerifyRequest request) {
        String currentStatus = tx.getStatus();
        UserSecurity security = tx.getFromAccount().getUser().getUserSecurity();
        if (security == null) {
            throw new RuntimeException("Hồ sơ bảo mật không tồn tại!");
        }

        // -------------------------------------------------------------------------
        // LUỒNG 1: PENDING_PIN (Chuyển tiền ngay khi khớp PIN)
        // -------------------------------------------------------------------------
        if ("PENDING_PIN".equals(currentStatus)) {
            pinService.verifyPin(security, request.getAuthCode());
            Transaction completedTx = transactionService.executeTransactionCore(tx);
            auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (1 lớp PIN).");
            
            Map<String, Object> flatData = new HashMap<>();
            flatData.put("id", completedTx.getId());
            flatData.put("status", "SUCCESS");
            flatData.put("amount", completedTx.getAmount());
            flatData.put("description", completedTx.getDescription() != null ? completedTx.getDescription() : "");
            flatData.put("createdAt", completedTx.getCreatedAt().toString());

            return CompletableFuture.completedFuture(ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Xác thực mã PIN và thanh toán thành công!",
                "data", flatData
            )));
        }
        
        // -------------------------------------------------------------------------
        // LUỒNG 2: PENDING_PIN_OTP (Chuyển tiếp sang lớp OTP)
        // -------------------------------------------------------------------------
        if ("PENDING_PIN_OTP".equals(currentStatus)) {
            pinService.verifyPin(security, request.getAuthCode());
            tx.setStatus("PENDING_OTP");
            transactionRepository.saveAndFlush(tx); // 🚀 Đã ép ghi xuống đĩa cứng
            otpService.generateAndSendOtpAsync(tx);
            return CompletableFuture.completedFuture(ResponseEntity.ok(Map.of(
                "status", "NEXT_STEP", "nextAuthType", "OTP",
                "message", "Mã PIN đúng. Vui lòng nhập OTP vừa được gửi."
            )));
        }
        
        // -------------------------------------------------------------------------
        // LUỒNG 3: PENDING_PIN_FACE (Chuyển tiếp sang Quét mặt tĩnh)
        // -------------------------------------------------------------------------
        if ("PENDING_PIN_FACE".equals(currentStatus)) {
            pinService.verifyPin(security, request.getAuthCode());
            tx.setStatus("PENDING_FACE_STATIC");
            transactionRepository.saveAndFlush(tx); // 🚀 Đã ép ghi xuống đĩa cứng
            return CompletableFuture.completedFuture(ResponseEntity.ok(Map.of(
                "status", "NEXT_STEP", "nextAuthType", "FACE_STATIC",
                "message", "Mã PIN đúng. Vui lòng quét khuôn mặt bảo mật."
            )));
        }
        
        // -------------------------------------------------------------------------
        // LUỒNG 4: PENDING_PIN_HIGH (Chuyển tiếp sang Sinh trắc học kép AI - QUAN TRỌNG)
        // -------------------------------------------------------------------------
        if ("PENDING_PIN_HIGH".equals(currentStatus)) {
            pinService.verifyPin(security, request.getAuthCode());
            tx.setStatus("PENDING_ALL_IN_ONE");
            transactionRepository.saveAndFlush(tx); // 🚀 Đã ép ghi trạng thái giao dịch mới
            
            String biometricToken = UUID.randomUUID().toString();
            BiometricSession biometricSession = BiometricSession.builder()
                    .sessionToken(biometricToken)
                    .transaction(tx)
                    .user(tx.getFromAccount().getUser())
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .used(false)
                    .build();
            
            // 🚀 VÁ ĐIỂM CHẾT: Đổi từ .save() sang .saveAndFlush() để đẩy trực tiếp dữ liệu token 
            // xuống DB vật lý ngay lập tức, luồng WebSocket bắn lên sau đó vài mili-giây chắc chắn sẽ SELECT thấy!
            biometricSessionRepository.saveAndFlush(biometricSession);

            String voiceCode = otpService.generateVoiceOtp(tx.getId());
            return CompletableFuture.completedFuture(ResponseEntity.ok(Map.of(
                "status", "NEXT_STEP",
                "nextAuthType", "ALL_IN_ONE_BIOMETRIC",
                "voiceCode", voiceCode,
                "biometricSessionToken", biometricToken,
                "message", "Mã PIN đúng. Vui lòng chuẩn bị xác thực sinh trắc học kép."
            )));
        }
        
        return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body("Luồng xác thực bị gián đoạn hoặc không hợp lệ!"));
    }
   
   
   
   
    private CompletableFuture<ResponseEntity<?>> handleOtp(Transaction tx, String username, AuthVerifyRequest request) {
        boolean isValid = otpService.verifyOtp(tx.getId(), request.getAuthCode());
        if (!isValid) {
            auditLogService.logAction(username, "OTP_FAILED", "Sai OTP giao dịch " + tx.getId());
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("OTP sai hoặc đã hết hạn!"));
        }
        if ("PENDING_OTP".equals(tx.getStatus())) {
            Transaction completedTx = transactionService.executeTransactionCore(tx);
            auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (PIN + OTP).");
            return CompletableFuture.completedFuture(ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx)));
        }
        return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body("Luồng xác thực bị gián đoạn hoặc không hợp lệ!"));
    }

    private CompletableFuture<ResponseEntity<?>> handleFaceStatic(Transaction tx, String username, AuthVerifyRequest request) {
        if (!"PENDING_FACE_STATIC".equals(tx.getStatus())) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body("Luồng xác thực bị gián đoạn hoặc không hợp lệ!"));
        }
    User txUser = tx.getFromAccount().getUser();
        if (!txUser.hasFaceEmbeddings() && !txUser.hasFaceEmbedding() && !txUser.hasLegacyFaceImage()) {
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body("Lỗi: Người dùng chưa thiết lập FaceID gốc!"));
        }
        return riskEvaluationService.verifyFaceStaticAsync(txUser, request.getFaceImageBase64())
            .orTimeout(aiParallelTimeoutSeconds, TimeUnit.SECONDS)
            .thenApply(aiResult -> {
                boolean isMatch = aiResult != null && aiResult.isMatched();
                if (!isMatch) {
                    // Defensive guard: verifyFaceStaticAsync does not set UNDER_REVIEW today,
                    // but this guard prevents a silent overwrite if that ever changes.
                    if ("UNDER_REVIEW".equals(tx.getStatus())) {
                        auditLogService.logAction(username, "FACE_STATIC_FROZEN",
                            "Trạng thái UNDER_REVIEW được bảo vệ — giao dịch " + tx.getId() + " không bị ghi đè.");
                        return (ResponseEntity<?>) ResponseEntity.status(403).body(Map.of(
                            "status", "FROZEN",
                            "message", "Giao dịch đang được tạm giữ để kiểm duyệt an toàn. Vui lòng chờ hệ thống xử lý."
                        ));
                    }
                    int attempts = (tx.getFailedAiAttempts() != null ? tx.getFailedAiAttempts() : 0) + 1;
                    tx.setFailedAiAttempts(attempts);
                    if (attempts >= 3) {
                        tx.setStatus("BLOCKED");
                        transactionRepository.save(tx);
                        auditLogService.logAction(username, "FACE_REJECT_MAX_RETRIES", "Khóa giao dịch: Xác thực khuôn mặt tĩnh sai quá 3 lần.");
                        return (ResponseEntity<?>) ResponseEntity.status(403).body(Map.of(
                            "status", "BLOCKED",
                            "message", "Giao dịch bị hủy do xác thực khuôn mặt sai quá 3 lần!"
                        ));
                    }
                    transactionRepository.save(tx);
                    auditLogService.logAction(username, "FACE_REJECT_RETRY", "Quét khuôn mặt sai lần " + attempts);
                    return (ResponseEntity<?>) ResponseEntity.badRequest().body(Map.of(
                        "status", "RETRY",
                        "message", "Khuôn mặt không khớp với cơ sở dữ liệu. Bạn còn " + (3 - attempts) + " lần thử."
                    ));
                }
                tx.setFailedAiAttempts(0);
                try {
                    Transaction completedTx = transactionService.executeTransactionCore(tx);
                    auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (PIN + Khuôn Mặt Tĩnh).");
                    return (ResponseEntity<?>) ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx));
                } catch (Exception e) {
                    log.error("[FACE_STATIC] tx={} executeTransactionCore failed: {}", tx.getId(), e.getMessage());
                    return (ResponseEntity<?>) ResponseEntity.status(500).body(Map.of("status", "ERROR", "message", "Lỗi thực hiện giao dịch."));
                }
            })
            .exceptionally(ex -> {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof java.util.concurrent.TimeoutException) {
                    log.error("[FACE_STATIC][TIMEOUT] tx={} — AI service did not respond in {}s", tx.getId(), aiParallelTimeoutSeconds);
                } else {
                    log.error("[FACE_STATIC][ERROR] tx={} — {}", tx.getId(), cause.getMessage());
                }
                return ResponseEntity.status(503).body(Map.of(
                    "status", "ERROR", "message", "Dịch vụ AI tạm thời không phản hồi. Vui lòng thử lại."
                ));
            });
    }

    private CompletableFuture<ResponseEntity<?>> handleFaceAi(Transaction tx, String username, AuthVerifyRequest request) {
        String currentStatus = tx.getStatus();
        if (!"PENDING_ALL_IN_ONE".equals(currentStatus) && !"PENDING_FACE_AI".equals(currentStatus)) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body("Luồng xác thực bị gián đoạn hoặc không hợp lệ!"));
        }
        return faceScanActionStrategy.validateFaceAndEmotionAsync(tx, request.getFaceFrameSequence())
            .thenApply(isSecure -> {
                if (!isSecure) {
                    // Guard: strategy mutates tx to UNDER_REVIEW before returning false on coercion.
                    // Never overwrite that status with BLOCKED.
                    if ("UNDER_REVIEW".equals(tx.getStatus())) {
                        auditLogService.logAction(username, "AI_COERCION_FROZEN",
                            "Phát hiện tâm lý bất thường — giao dịch " + tx.getId() + " bị đóng băng để kiểm duyệt.");
                        return (ResponseEntity<?>) ResponseEntity.status(403).body(Map.of(
                            "status", "FROZEN",
                            "message", "Giao dịch đang được tạm giữ để kiểm duyệt an toàn. Vui lòng chờ hệ thống xử lý."
                        ));
                    }
                    int attempts = (tx.getFailedAiAttempts() != null ? tx.getFailedAiAttempts() : 0) + 1;
                    tx.setFailedAiAttempts(attempts);
                    if (attempts >= 3) {
                        tx.setStatus("BLOCKED");
                        transactionRepository.save(tx);
                        auditLogService.logAction(username, "AI_REJECT_MAX_RETRIES", "Khóa giao dịch: Xác thực khuôn mặt/cảm xúc sai 3 lần.");
                        return (ResponseEntity<?>) ResponseEntity.status(403).body("Giao dịch bị hủy do xác thực sinh trắc học sai quá 3 lần!");
                    }
                    transactionRepository.save(tx);
                    auditLogService.logAction(username, "AI_REJECT_RETRY", "Quét AI sai lần " + attempts);
                    return (ResponseEntity<?>) ResponseEntity.badRequest().body("Khuôn mặt hoặc cảm xúc không khớp. Bạn còn " + (3 - attempts) + " lần thử.");
                }
                tx.setFailedAiAttempts(0);
                tx.setStatus("PENDING_VOICE_OTP");
                transactionRepository.save(tx);
                return (ResponseEntity<?>) ResponseEntity.ok(Map.of(
                    "status", "NEXT_STEP",
                    "nextAuthType", "VOICE_OTP",
                    "message", "Xác thực AI thành công. Vui lòng đọc Voice OTP."
                ));
            })
            .exceptionally(ex -> {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof java.util.concurrent.TimeoutException) {
                    log.error("[FACE_AI][TIMEOUT] tx={} — AI service did not respond in {}s", tx.getId(), aiParallelTimeoutSeconds);
                } else {
                    log.error("[FACE_AI][ERROR] tx={} — {}", tx.getId(), cause.getMessage());
                }
                return ResponseEntity.status(503).body(Map.of(
                    "status", "ERROR", "message", "Dịch vụ AI tạm thời không phản hồi. Vui lòng thử lại."
                ));
            });
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

    /**
     * Polling fallback: frontend calls this when the WebSocket FINAL_RESULT
     * is not received within the expected window (e.g. session dropped after commit).
     * Returns the current transaction status from the DB so the UI can recover.
     */
    @GetMapping("/{id}/verification-status")
    public ResponseEntity<?> getVerificationStatus(@PathVariable Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            Transaction tx = transactionRepository.findByIdWithUserSecurity(id).orElse(null);
            if (tx == null) return ResponseEntity.notFound().build();

            String owner = tx.getFromAccount().getUser().getUsername();
            if (!username.equals(owner)) return ResponseEntity.status(403).build();

            Map<String, Object> res = new HashMap<>();
            res.put("status", tx.getStatus());
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi truy vấn trạng thái: " + e.getMessage());
        }
    }

}