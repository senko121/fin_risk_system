// package com.datn.finrisk.web.controllers;

// import com.datn.finrisk.application.dtos.AuthVerifyRequest;
// import com.datn.finrisk.application.dtos.FaceAIResponse;
// import com.datn.finrisk.application.dtos.TransactionRequest;
// import com.datn.finrisk.core.entities.Transaction;
// import com.datn.finrisk.core.repository.TransactionRepository;
// import com.datn.finrisk.core.services.OtpService;
// import com.datn.finrisk.core.services.TransactionService;
// import com.datn.finrisk.core.strategies.AdvancedFaceActionStrategy;

// import jakarta.servlet.http.HttpServletRequest;
// import jakarta.validation.Valid;

// import com.datn.finrisk.core.services.AuditLogService; //   IMPORT THƯ KÝ
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;
// import org.springframework.web.client.RestTemplate;
// import org.springframework.http.HttpEntity;
// import org.springframework.http.HttpHeaders;
// import org.springframework.http.MediaType;

// import com.datn.finrisk.core.repository.TransactionLedgerRepository;
// import com.datn.finrisk.core.entities.TransactionLedger;
// import com.datn.finrisk.core.repository.AccountRepository;
// import com.datn.finrisk.core.entities.Account;
// import com.datn.finrisk.core.services.EmailService;
// import com.datn.finrisk.core.services.PinService;
// import java.util.List;
// import java.util.Map;
// import java.util.HashMap;
// import java.util.stream.Collectors;
// import java.time.LocalDateTime;

// @Slf4j
// @RestController
// @RequestMapping("/api/transactions")
// @CrossOrigin(origins = "http://localhost:5173") 
// public class TransactionController {

//     @Autowired
//     private TransactionService transactionService;

//     @Autowired
//     private TransactionRepository transactionRepository;

//     @Autowired
//     private TransactionLedgerRepository ledgerRepository;

//     @Autowired
//     private AccountRepository accountRepository;

//     @Autowired
//     private OtpService otpService;

//     @Autowired
//     private EmailService emailService;

//     @Autowired private AdvancedFaceActionStrategy faceScanActionStrategy;

//     @Autowired
//     private PinService pinService;
//     //   GỌI THƯ KÝ VÀO GHI SỔ GIAO DỊCH
//     @Autowired
//     private AuditLogService auditLogService;

// // @PostMapping("/process")
// //     public ResponseEntity<?> processTransaction(@Valid @RequestBody TransactionRequest request) {
// //         try {
// //             Transaction result = transactionService.initiateTransaction(
// //                     request.getFromAccountId(),
// //                     request.getToAccount(),
// //                     request.getAmount(),
// //                     request.getDescription()
// //             );

// //             //   GHI LOG TẠO LỆNH THÀNH CÔNG (Nhưng chưa chốt tiền)
// //             String username = result.getFromAccount().getUser().getUsername();
// //             auditLogService.logAction(username, "TRANSACTION_INITIATED", "Tạo lệnh chuyển " + request.getAmount() + " VND đến STK " + request.getToAccount() + ". Mức rủi ro: " + result.getRiskLevel());

// //             return ResponseEntity.ok(result);
// //         } catch (Exception e) {
// //             return ResponseEntity.badRequest().body(e.getMessage());
// //         }
// //     }

//     @PostMapping("/process")
//     // 🚀 BƯỚC 1: Thêm HttpServletRequest httpRequest vào đây để lấy thông tin mạng
//     public ResponseEntity<?> processTransaction(@Valid @RequestBody TransactionRequest request, HttpServletRequest httpRequest) {
//         try {
//             // 🚀 BƯỚC 2: Chộp IP và Thiết bị (User-Agent) ngay khi có Request bay vào
//             String currentIp = httpRequest.getRemoteAddr();
//             String currentDevice = httpRequest.getHeader("User-Agent");
            
//             // Cắt ngắn chuỗi Device nếu nó quá dài (tránh văng lỗi vỡ DataBase)
//             if (currentDevice != null && currentDevice.length() > 250) {
//                 currentDevice = currentDevice.substring(0, 250);
//             }

//             // 🚀 BƯỚC 3: Truyền thêm 2 biến currentIp và currentDevice xuống Service
//             Transaction result = transactionService.initiateTransaction(
//                     request.getFromAccountId(),
//                     request.getToAccount(),
//                     request.getAmount(),
//                     request.getDescription(),
//                     currentIp,      // Nhét IP vào đây
//                     currentDevice   // Nhét Device vào đây
//             );

//             // GHI LOG TẠO LỆNH THÀNH CÔNG (Nhưng chưa chốt tiền)
//             String username = result.getFromAccount().getUser().getUsername();
//             auditLogService.logAction(username, "TRANSACTION_INITIATED", "Tạo lệnh chuyển " + request.getAmount() + " VND đến STK " + request.getToAccount() + ". Mức rủi ro: " + result.getRiskLevel());

//             return ResponseEntity.ok(result);
//         } catch (Exception e) {
//             return ResponseEntity.badRequest().body(e.getMessage());
//         }
//     }

//     // @PostMapping("/verify")
//     // public ResponseEntity<?> verifyAndExecute(@RequestBody AuthVerifyRequest request) {
//     //     try {
//     //         Transaction tx = transactionRepository.findById(request.getTransactionId())
//     //                 .orElseThrow(() -> new RuntimeException("Giao dịch không tồn tại!"));

//     //         String username = tx.getFromAccount().getUser().getUsername();

//     //         // ==========================================================
//     //         // TRẠM 0: XÁC THỰC MÃ PIN (MỨC LOW)
//     //         // ==========================================================
//     //         if ("PIN".equals(request.getAuthType())) {
//     //             Long userId = tx.getFromAccount().getUser().getId();
                
//     //             // Gọi PinService để kiểm tra mã PIN với Database (Đã mã hóa BCrypt)
//     //             boolean isPinValid = pinService.verifyPin(userId, request.getAuthCode());

//     //             if (!isPinValid) {
//     //                 auditLogService.logAction(username, "PIN_FAILED", "Nhập sai mã PIN. ID Giao dịch: " + tx.getId());
//     //                 return ResponseEntity.badRequest().body("Mã PIN không chính xác hoặc tài khoản đang bị tạm khóa!");
//     //             }

//     //             // Nếu PIN đúng và giao dịch đang ở mức LOW (PENDING_PIN) -> CHỐT SỔ TRỪ TIỀN
//     //             if ("PENDING_PIN".equals(tx.getStatus())) {
//     //                 // Dùng chung hàm Lõi Kế toán đã bóc tách
//     //                 Transaction completedTx = transactionService.executeTransactionCore(tx); 
                    
//     //                 auditLogService.logAction(username, "TRANSACTION_SUCCESS", "Chuyển thành công " + tx.getAmount() + " VND (Xác thực 1 lớp PIN).");
//     //                 return ResponseEntity.ok(completedTx);
//     //             } 
                
//     //             // (Sau này code tiếp) Nếu PIN đúng nhưng giao dịch ở mức MEDIUM -> Chuyển trạng thái sang chờ OTP
//     //             return ResponseEntity.badRequest().body("Trạng thái giao dịch không hợp lệ để chốt bằng mã PIN!");
//     //         }

//     //         // ==========================================================
//     //         // TRẠM 1: XỬ LÝ NHẬP OTP (MỨC MEDIUM_1)
//     //         // ==========================================================
//     //         else if ("OTP".equals(request.getAuthType())) {
//     //             boolean isValid = otpService.verifyOtp(tx.getId(), request.getAuthCode());

//     //             if (!isValid) {
//     //                 auditLogService.logAction(username, "OTP_VERIFY_FAILED", "Nhập sai mã OTP cho giao dịch " + tx.getId());
//     //                 return ResponseEntity.badRequest().body("OTP sai hoặc đã hết hạn!");
//     //             }

//     //             // 🚀 ĐÃ SỬA: Gọi chung hàm executeTransactionCore thay vì hàm cũ
//     //             Transaction completedTx = transactionService.executeTransactionCore(tx);
                
//     //             auditLogService.logAction(username, "TRANSACTION_SUCCESS", "Chuyển thành công " + tx.getAmount() + " VND (Xác thực qua OTP). ID Giao dịch: " + tx.getId());
//     //             return ResponseEntity.ok(completedTx);
//     //         }

//     //         // ==========================================================
//     //         // TRẠM 2: QUÉT MẶT & ĐỌC VOICE OTP (MỨC HIGH)
//     //         // ==========================================================
//     //         else if ("FACE".equals(request.getAuthType())) {
//     //             String liveImage = request.getFaceImageBase64();
                
//     //             // GỌI CHIẾN THUẬT XÁC THỰC SONG SONG ĐÃ VIẾT Ở BƯỚC 1
//     //             boolean isSecure = faceScanActionStrategy.validateFaceAndEmotion(tx, liveImage);

//     //             if (!isSecure) {
//     //                 tx.setStatus("BLOCKED"); // Khóa giao dịch
//     //                 transactionRepository.save(tx);
                    
//     //                 auditLogService.logAction(username, "AI_REJECT", "Giao dịch bị chặn do AI xác định rủi ro sinh trắc học hoặc tâm lý.");
//     //                 return ResponseEntity.status(403).body("Cảnh báo an ninh: Xác thực thất bại hoặc phát hiện dấu hiệu bị cưỡng ép!");
//     //             }

//     //             // --- VƯỢT ẢI THÀNH CÔNG ---
//     //             auditLogService.logAction(username, "AI_PASS", "Xác thực AI thành công. Chuyển tiếp vòng OTP.");
//     //             tx.setStatus("PENDING_OTP");
//     //             transactionRepository.save(tx);

//     //             // Tạo và gửi OTP (Giữ nguyên logic cũ của bro)
//     //             String newOtp = String.format("%06d", new java.util.Random().nextInt(999999));
//     //             otpService.saveOtp(tx.getId(), newOtp);
//     //             try {
//     //                 emailService.sendOtpEmail(tx.getFromAccount().getUser().getEmail(), newOtp);
//     //             } catch (Exception e) {
//     //                 log.error("Lỗi gửi mail OTP: {}", e.getMessage());
//     //             }

//     //             return ResponseEntity.ok(tx);
//     //         }

//     //         return ResponseEntity.badRequest().body("Loại xác thực không hợp lệ!");

//     //     } catch (Exception e) {
//     //         log.error("Lỗi xác thực giao dịch: ", e);
//     //         return ResponseEntity.badRequest().body(e.getMessage());
//     //     }
//     // }

//     @PostMapping("/verify")
//     public ResponseEntity<?> verifyAndExecute(@RequestBody AuthVerifyRequest request) {
//         try {
//             Transaction tx = transactionRepository.findById(request.getTransactionId())
//                     .orElseThrow(() -> new RuntimeException("Giao dịch không tồn tại!"));

//             String username = tx.getFromAccount().getUser().getUsername();
//             String authType = request.getAuthType();
//             String currentStatus = tx.getStatus();

//             // ==========================================================
//             // TRẠM 0: XÁC THỰC MÃ PIN (Bước đầu tiên của LOW, MEDIUM_1, MEDIUM_2)
//             // ==========================================================
//             if ("PIN".equals(authType)) {
//                 Long userId = tx.getFromAccount().getUser().getId();
//                 boolean isPinValid = pinService.verifyPin(userId, request.getAuthCode());

//                 if (!isPinValid) {
//                     auditLogService.logAction(username, "PIN_FAILED", "Nhập sai PIN giao dịch " + tx.getId());
//                     return ResponseEntity.badRequest().body("Mã PIN không chính xác hoặc tài khoản đang bị khóa!");
//                 }

//                 // Điều hướng dựa trên Status hiện tại
//                 if ("PENDING_PIN".equals(currentStatus)) { 
//                     // [MỨC LOW] Đã xong -> Trừ tiền
//                     Transaction completedTx = transactionService.executeTransactionCore(tx);
//                     auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (1 lớp PIN).");
//                     return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx));
//                 } 
//                 else if ("PENDING_PIN_OTP".equals(currentStatus)) { 
//                     // [MỨC MEDIUM_1] Chuyển qua trạm OTP
//                     tx.setStatus("PENDING_OTP");
//                     transactionRepository.save(tx);
//                     return ResponseEntity.ok(Map.of("status", "NEXT_STEP", "nextAuthType", "OTP", "message", "Mã PIN đúng. Vui lòng nhập OTP."));
//                 }
//                 else if ("PENDING_PIN_OTP_FACE".equals(currentStatus)) { 
//                     // [MỨC MEDIUM_2] Chuyển qua trạm OTP (rồi lát nữa tính tiếp)
//                     tx.setStatus("PENDING_OTP_FACE");
//                     transactionRepository.save(tx);
//                     return ResponseEntity.ok(Map.of("status", "NEXT_STEP", "nextAuthType", "OTP", "message", "Mã PIN đúng. Vui lòng nhập OTP."));
//                 }
//             }

//             // ==========================================================
//             // TRẠM 1: XÁC THỰC OTP (Bước 2 của MEDIUM_1 và MEDIUM_2)
//             // ==========================================================
//             else if ("OTP".equals(authType)) {
//                 boolean isValid = otpService.verifyOtp(tx.getId(), request.getAuthCode());
//                 if (!isValid) {
//                     auditLogService.logAction(username, "OTP_FAILED", "Sai OTP giao dịch " + tx.getId());
//                     return ResponseEntity.badRequest().body("OTP sai hoặc đã hết hạn!");
//                 }

//                 if ("PENDING_OTP".equals(currentStatus)) { 
//                     // [MỨC MEDIUM_1] Đã xong 2 bước -> Trừ tiền
//                     Transaction completedTx = transactionService.executeTransactionCore(tx);
//                     auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (PIN + OTP).");
//                     return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx));
//                 }
//                 else if ("PENDING_OTP_FACE".equals(currentStatus)) { 
//                     // [MỨC MEDIUM_2] Đã xong 2 bước, chuyển qua bước 3: Quét mặt tĩnh
//                     tx.setStatus("PENDING_FACE_STATIC");
//                     transactionRepository.save(tx);
//                     return ResponseEntity.ok(Map.of("status", "NEXT_STEP", "nextAuthType", "FACE_STATIC", "message", "OTP đúng. Vui lòng quét khuôn mặt để hoàn tất."));
//                 }
//             }

//             // ==========================================================
//             // TRẠM 2: QUÉT MẶT TĨNH (Bước 3 của MEDIUM_2)
//             // ==========================================================
//             else if ("FACE_STATIC".equals(authType)) {
//                 if ("PENDING_FACE_STATIC".equals(currentStatus)) {
//                     // TODO: Mốt viết logic so sánh ảnh tĩnh 1:1 ở đây (Facial Match).
//                     // Tạm thời coi như pass luôn để test luồng
//                     Transaction completedTx = transactionService.executeTransactionCore(tx);
//                     auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (Tuân thủ NHNN).");
//                     return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx));
//                 }
//             }

//             // ==========================================================
//             // TRẠM 3: QUÉT MẶT AI + CẢM XÚC (Báo động đỏ của HIGH)
//             // ==========================================================
//             else if ("FACE_AI".equals(authType)) {
//                 if ("PENDING_FACE_AI".equals(currentStatus)) {
//                     boolean isSecure = faceScanActionStrategy.validateFaceAndEmotion(tx, request.getFaceImageBase64());
//                     if (!isSecure) {
//                         tx.setStatus("BLOCKED");
//                         transactionRepository.save(tx);
//                         auditLogService.logAction(username, "AI_REJECT", "Chặn đứng giao dịch do phát hiện rủi ro sinh trắc.");
//                         return ResponseEntity.status(403).body("Cảnh báo an ninh: Xác thực AI thất bại!");
//                     }
                    
//                     // Vượt ải AI -> Ép bắt đọc Voice OTP
//                     tx.setStatus("PENDING_VOICE_OTP");
//                     transactionRepository.save(tx);
//                     return ResponseEntity.ok(Map.of("status", "NEXT_STEP", "nextAuthType", "VOICE_OTP", "message", "Xác thực AI thành công. Vui lòng đọc Voice OTP."));
//                 }
//             }

//             return ResponseEntity.badRequest().body("Luồng xác thực bị gián đoạn hoặc không hợp lệ!");

//         } catch (Exception e) {
//             log.error("Lỗi xác thực giao dịch: ", e);
//             return ResponseEntity.badRequest().body(e.getMessage());
//         }
//     }

// @GetMapping("/history/{accountId}")
//     public ResponseEntity<?> getTransactionHistory(@PathVariable Long accountId) {
//         try {
//             List<TransactionLedger> ledgers = ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
//             List<Map<String, Object>> result = ledgers.stream().map(l -> {
//                 Map<String, Object> map = new HashMap<>();
                
//                 // 🚀 BƯỚC 1: Kéo Giao dịch gốc ra trước để dùng cho toàn bộ logic bên dưới
//                 Transaction rootTx = l.getTransaction();
                
//                 // Dữ liệu Sổ cái (Kế toán)
//                 map.put("id", l.getId());
//                 map.put("type", l.getEntryType()); 
//                 map.put("amount", l.getAmount());
//                 map.put("balanceAfter", l.getBalanceAfter());
//                 map.put("date", l.getCreatedAt());
                
//                 // 🚀 BƯỚC 2: ĐÃ FIX LOGIC LỜI NHẮN (Ưu tiên lấy từ rootTx)
//                 String txDescription = (rootTx != null && rootTx.getDescription() != null && !rootTx.getDescription().isEmpty()) 
//                         ? rootTx.getDescription() 
//                         : (l.getEntryType().equals("DEBIT") ? "Chuyển khoản đi" : "Nhận tiền chuyển khoản");
//                 map.put("description", txDescription);
                
//                 // BƯỚC 3: BỔ SUNG DỮ LIỆU BẢO MẬT VÀ TÊN NGƯỜI LIÊN QUAN
//                 if (rootTx != null) {
//                     map.put("toAccountNumber", rootTx.getToAccountNumber());
//                     map.put("riskLevel", rootTx.getRiskLevel());
//                     map.put("totalRiskScore", rootTx.getTotalRiskScore());
//                     map.put("emotionSignal", rootTx.getEmotionSignal());

//                     // TÌM TÊN NGƯỜI LIÊN QUAN (NGƯỜI GỬI / NGƯỜI NHẬN)
//                     String relatedName = "Người dùng ẩn danh";
//                     if ("DEBIT".equals(l.getEntryType())) {
//                         // Tiền trừ đi: Tìm tên người nhận
//                         relatedName = accountRepository.findByAccountNumber(rootTx.getToAccountNumber())
//                                 .map(acc -> acc.getUser().getFullName())
//                                 .orElse("Người nhận ngoài hệ thống");
//                     } else {
//                         // Tiền cộng vào: Lấy tên người gửi
//                         relatedName = rootTx.getFromAccount().getUser().getFullName();
//                     }
//                     map.put("relatedName", relatedName); 

//                 } else {
//                     // Fallback nếu không có transaction gốc (VD: tiền nạp ban đầu)
//                     map.put("toAccountNumber", "N/A");
//                     map.put("riskLevel", "LOW");
//                     map.put("totalRiskScore", 0);
//                     map.put("emotionSignal", "N/A");
//                     map.put("relatedName", "Hệ thống FinRisk");
//                 }

//                 return map;
//             }).collect(Collectors.toList());
            
//             return ResponseEntity.ok(result);
//         } catch (Exception e) {
//             return ResponseEntity.badRequest().body("Lỗi lấy lịch sử: " + e.getMessage());
//         }
//     }

//     @GetMapping("/lookup/{accountNumber}")
//     public ResponseEntity<?> lookupAccountName(@PathVariable String accountNumber) {
//         try {
//             Account account = accountRepository.findByAccountNumber(accountNumber)
//                     .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại trên hệ thống!"));
//             Map<String, String> response = new HashMap<>();
//             response.put("fullName", account.getUser().getFullName()); 
//             return ResponseEntity.ok(response);
//         } catch (Exception e) {
//             return ResponseEntity.badRequest().body(e.getMessage());
//         }
//     }

//     @GetMapping("/recent-recipients/{accountId}")
//     public ResponseEntity<?> getRecentRecipients(@PathVariable Long accountId) {
//         try {
//             LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
//             List<TransactionLedger> recentLedgers = ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
//             List<Map<String, String>> recipients = recentLedgers.stream()
//                 .filter(l -> l.getEntryType().equals("DEBIT")) 
//                 .filter(l -> l.getCreatedAt().isAfter(sevenDaysAgo)) 
//                 .map(l -> {
//                     Map<String, String> map = new HashMap<>();
//                     map.put("accountNumber", l.getTransaction().getToAccountNumber());
//                     String name = accountRepository.findByAccountNumber(l.getTransaction().getToAccountNumber())
//                                     .map(acc -> acc.getUser().getFullName())
//                                     .orElse("Người nhận ngoài hệ thống");
//                     map.put("fullName", name);
//                     return map;
//                 })
//                 .distinct() 
//                 .limit(5)   
//                 .collect(Collectors.toList());
//             return ResponseEntity.ok(recipients);
//         } catch (Exception e) {
//             return ResponseEntity.badRequest().body("Lỗi lấy danh sách gần đây: " + e.getMessage());
//         }
//     }
// }




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

import com.datn.finrisk.core.repository.TransactionLedgerRepository;
import com.datn.finrisk.core.entities.TransactionLedger;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.services.EmailService;
import com.datn.finrisk.core.services.PinService;
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
    //   GỌI THƯ KÝ VÀO GHI SỔ GIAO DỊCH
    @Autowired
    private AuditLogService auditLogService;


    // @PostMapping("/process")
    // // 🚀 BƯỚC 1: Thêm HttpServletRequest httpRequest vào đây để lấy thông tin mạng
    // public ResponseEntity<?> processTransaction(@Valid @RequestBody TransactionRequest request, HttpServletRequest httpRequest) {
    //     try {
    //         // 🚀 BƯỚC 2: Chộp IP và Thiết bị (User-Agent) ngay khi có Request bay vào
    //         String currentIp = httpRequest.getRemoteAddr();
    //         String currentDevice = httpRequest.getHeader("User-Agent");
            
    //         // Cắt ngắn chuỗi Device nếu nó quá dài (tránh văng lỗi vỡ DataBase)
    //         if (currentDevice != null && currentDevice.length() > 250) {
    //             currentDevice = currentDevice.substring(0, 250);
    //         }

    //         // 🚀 BƯỚC 3: Truyền thêm 2 biến currentIp và currentDevice xuống Service
    //         Transaction result = transactionService.initiateTransaction(
    //                 request.getFromAccountId(),
    //                 request.getToAccount(),
    //                 request.getAmount(),
    //                 request.getDescription(),
    //                 currentIp,      // Nhét IP vào đây
    //                 currentDevice   // Nhét Device vào đây
    //         );

    //         // GHI LOG TẠO LỆNH THÀNH CÔNG (Nhưng chưa chốt tiền)
    //         String username = result.getFromAccount().getUser().getUsername();
    //         auditLogService.logAction(username, "TRANSACTION_INITIATED", "Tạo lệnh chuyển " + request.getAmount() + " VND đến STK " + request.getToAccount() + ". Mức rủi ro: " + result.getRiskLevel());

    //         return ResponseEntity.ok(result);
    //     } catch (Exception e) {
    //         return ResponseEntity.badRequest().body(e.getMessage());
    //     }
    // }


    @PostMapping("/process")
    // 🚀 BƯỚC 1: XÓA TRY-CATCH VÀ NHỚ THÊM 'throws Exception'
    public ResponseEntity<?> processTransaction(@Valid @RequestBody TransactionRequest request, HttpServletRequest httpRequest) throws Exception {
        
        // Chộp IP và Thiết bị (User-Agent) ngay khi có Request bay vào
        String currentIp = httpRequest.getRemoteAddr();
        String currentDevice = httpRequest.getHeader("User-Agent");
        
        // Cắt ngắn chuỗi Device nếu nó quá dài (tránh văng lỗi vỡ DataBase)
        if (currentDevice != null && currentDevice.length() > 250) {
            currentDevice = currentDevice.substring(0, 250);
        }

        // 🚀 BƯỚC 2: Gọi Service. Nếu tài khoản bị khóa, nó sẽ NÉM LỖI TẠI ĐÂY và DỪNG LUÔN, không chạy tiếp xuống dưới!
        Transaction result = transactionService.initiateTransaction(
                request.getFromAccountId(),
                request.getToAccount(),
                request.getAmount(),
                request.getDescription(),
                currentIp,      // Nhét IP vào đây
                currentDevice   // Nhét Device vào đây
        );

        // GHI LOG TẠO LỆNH THÀNH CÔNG (Nhưng chưa chốt tiền)
        String username = result.getFromAccount().getUser().getUsername();
        auditLogService.logAction(username, "TRANSACTION_INITIATED", "Tạo lệnh chuyển " + request.getAmount() + " VND đến STK " + request.getToAccount() + ". Mức rủi ro: " + result.getRiskLevel());

        return ResponseEntity.ok(result);
    }

@PostMapping("/verify")
    // 🚀 BƯỚC 1: XÓA TRY-CATCH, THÊM 'throws Exception' ĐỂ LỖI BAY THẲNG RA NGOÀI
    public ResponseEntity<?> verifyAndExecute(@RequestBody AuthVerifyRequest request) throws Exception {
        
        Transaction tx = transactionRepository.findById(request.getTransactionId())
                .orElseThrow(() -> new RuntimeException("Giao dịch không tồn tại!"));

        String username = tx.getFromAccount().getUser().getUsername();
        String authType = request.getAuthType();
        String currentStatus = tx.getStatus();

        // ==========================================================
        // TRẠM 0: XÁC THỰC MÃ PIN
        // ==========================================================
        if ("PIN".equals(authType)) {
            Long userId = tx.getFromAccount().getUser().getId();
            
            // 🚀 LƯU Ý: Nếu sai PIN, hàm này sẽ ném BusinessLogicException thẳng ra ngoài
            // KHÔNG CẦN CHỖ NÀY PHẢI HỨNG NỮA!
            boolean isPinValid = pinService.verifyPin(userId, request.getAuthCode());

            // Đoạn if(!isPinValid) bên dưới thực ra dư thừa vì Exception đã chặn đứng luồng chạy ở trên rồi
            // Nhưng cứ để cho chắc cú cũng không sao.
            if (!isPinValid) {
                auditLogService.logAction(username, "PIN_FAILED", "Nhập sai PIN giao dịch " + tx.getId());
                return ResponseEntity.badRequest().body("Mã PIN không chính xác hoặc tài khoản đang bị khóa!");
            }

            // Điều hướng dựa trên Status hiện tại
            if ("PENDING_PIN".equals(currentStatus)) { 
                Transaction completedTx = transactionService.executeTransactionCore(tx);
                auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (1 lớp PIN).");
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx));
            } 
            else if ("PENDING_PIN_OTP".equals(currentStatus)) { 
                tx.setStatus("PENDING_OTP");
                transactionRepository.save(tx);
                return ResponseEntity.ok(Map.of("status", "NEXT_STEP", "nextAuthType", "OTP", "message", "Mã PIN đúng. Vui lòng nhập OTP."));
            }
            else if ("PENDING_PIN_OTP_FACE".equals(currentStatus)) { 
                tx.setStatus("PENDING_OTP_FACE");
                transactionRepository.save(tx);
                return ResponseEntity.ok(Map.of("status", "NEXT_STEP", "nextAuthType", "OTP", "message", "Mã PIN đúng. Vui lòng nhập OTP."));
            }
        }

        // ==========================================================
        // TRẠM 1: XÁC THỰC OTP 
        // ==========================================================
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
            else if ("PENDING_OTP_FACE".equals(currentStatus)) { 
                tx.setStatus("PENDING_FACE_STATIC");
                transactionRepository.save(tx);
                return ResponseEntity.ok(Map.of("status", "NEXT_STEP", "nextAuthType", "FACE_STATIC", "message", "OTP đúng. Vui lòng quét khuôn mặt để hoàn tất."));
            }
        }

        // ==========================================================
        // TRẠM 2: QUÉT MẶT TĨNH 
        // ==========================================================
        else if ("FACE_STATIC".equals(authType)) {
            if ("PENDING_FACE_STATIC".equals(currentStatus)) {
                // TODO: Facematch Logic
                Transaction completedTx = transactionService.executeTransactionCore(tx);
                auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (Tuân thủ NHNN).");
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", completedTx));
            }
        }

        // ==========================================================
        // TRẠM 3: QUÉT MẶT AI + CẢM XÚC 
        // ==========================================================
        else if ("FACE_AI".equals(authType)) {
            if ("PENDING_FACE_AI".equals(currentStatus)) {
                boolean isSecure = faceScanActionStrategy.validateFaceAndEmotion(tx, request.getFaceImageBase64());
                if (!isSecure) {
                    tx.setStatus("BLOCKED");
                    transactionRepository.save(tx);
                    auditLogService.logAction(username, "AI_REJECT", "Chặn đứng giao dịch do phát hiện rủi ro sinh trắc.");
                    return ResponseEntity.status(403).body("Cảnh báo an ninh: Xác thực AI thất bại!");
                }
                
                tx.setStatus("PENDING_VOICE_OTP");
                transactionRepository.save(tx);
                return ResponseEntity.ok(Map.of("status", "NEXT_STEP", "nextAuthType", "VOICE_OTP", "message", "Xác thực AI thành công. Vui lòng đọc Voice OTP."));
            }
        }

        return ResponseEntity.badRequest().body("Luồng xác thực bị gián đoạn hoặc không hợp lệ!");
        
        // 🚀 BƯỚC 2: Toàn bộ khối CATCH bự chà bá ở cuối đã bị xóa sổ!
    }

@GetMapping("/history/{accountId}")
    public ResponseEntity<?> getTransactionHistory(@PathVariable Long accountId) {
        try {
            List<TransactionLedger> ledgers = ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
            List<Map<String, Object>> result = ledgers.stream().map(l -> {
                Map<String, Object> map = new HashMap<>();
                
                // 🚀 BƯỚC 1: Kéo Giao dịch gốc ra trước để dùng cho toàn bộ logic bên dưới
                Transaction rootTx = l.getTransaction();
                
                // Dữ liệu Sổ cái (Kế toán)
                map.put("id", l.getId());
                map.put("type", l.getEntryType()); 
                map.put("amount", l.getAmount());
                map.put("balanceAfter", l.getBalanceAfter());
                map.put("date", l.getCreatedAt());
                
                // 🚀 BƯỚC 2: ĐÃ FIX LOGIC LỜI NHẮN (Ưu tiên lấy từ rootTx)
                String txDescription = (rootTx != null && rootTx.getDescription() != null && !rootTx.getDescription().isEmpty()) 
                        ? rootTx.getDescription() 
                        : (l.getEntryType().equals("DEBIT") ? "Chuyển khoản đi" : "Nhận tiền chuyển khoản");
                map.put("description", txDescription);
                
                // BƯỚC 3: BỔ SUNG DỮ LIỆU BẢO MẬT VÀ TÊN NGƯỜI LIÊN QUAN
                if (rootTx != null) {
                    map.put("toAccountNumber", rootTx.getToAccountNumber());
                    map.put("riskLevel", rootTx.getRiskLevel());
                    map.put("totalRiskScore", rootTx.getTotalRiskScore());
                    map.put("emotionSignal", rootTx.getEmotionSignal());

                    // TÌM TÊN NGƯỜI LIÊN QUAN (NGƯỜI GỬI / NGƯỜI NHẬN)
                    String relatedName = "Người dùng ẩn danh";
                    if ("DEBIT".equals(l.getEntryType())) {
                        // Tiền trừ đi: Tìm tên người nhận
                        relatedName = accountRepository.findByAccountNumber(rootTx.getToAccountNumber())
                                .map(acc -> acc.getUser().getFullName())
                                .orElse("Người nhận ngoài hệ thống");
                    } else {
                        // Tiền cộng vào: Lấy tên người gửi
                        relatedName = rootTx.getFromAccount().getUser().getFullName();
                    }
                    map.put("relatedName", relatedName); 

                } else {
                    // Fallback nếu không có transaction gốc (VD: tiền nạp ban đầu)
                    map.put("toAccountNumber", "N/A");
                    map.put("riskLevel", "LOW");
                    map.put("totalRiskScore", 0);
                    map.put("emotionSignal", "N/A");
                    map.put("relatedName", "Hệ thống FinRisk");
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