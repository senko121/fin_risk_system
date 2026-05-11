 

// package com.datn.finrisk.web.websocket;

// import com.datn.finrisk.application.dtos.EmotionAIResponse;
// import com.datn.finrisk.core.entities.Transaction;
// import com.datn.finrisk.core.repository.TransactionRepository;
// import com.datn.finrisk.core.services.AuditLogService;
// import com.datn.finrisk.core.services.OtpService;
// import com.datn.finrisk.core.services.RiskEvaluationService;
// import com.datn.finrisk.core.services.TransactionService;
// import com.datn.finrisk.core.strategies.AdvancedFaceActionStrategy;
// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Component;
// import org.springframework.web.socket.CloseStatus;
// import org.springframework.web.socket.TextMessage;
// import org.springframework.web.socket.WebSocketSession;
// import org.springframework.web.socket.handler.TextWebSocketHandler;

// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.ConcurrentHashMap;

// @Component
// public class LiveEmotionWebSocketHandler extends TextWebSocketHandler {

//     @Autowired
//     private RiskEvaluationService riskEvaluationService;

//     @Autowired
//     private TransactionRepository transactionRepository;

//     @Autowired
//     private AdvancedFaceActionStrategy faceActionStrategy;

//     @Autowired
//     private OtpService otpService;

//     // 🚀 BƯƠM THÊM 2 DỊCH VỤ ĐỂ CHỐT SỔ VÀ GHI LOG
//     @Autowired
//     private TransactionService transactionService;

//     @Autowired
//     private AuditLogService auditLogService;

//     @Autowired
//     private ObjectMapper mapper;

//     // 🚀 BỘ NHỚ ĐỆM 50 TẤM ẢNH TRÊN RAM
//     private final Map<String, List<String>> sessionFrameBuffer = new ConcurrentHashMap<>();

//     @Override
//     public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//         System.out.println("🔌 [WS] Client kết nối thành công! Session ID: " + session.getId());
//     }

//     @Override
//     public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
//         System.out.println("🔌 [WS] Client ngắt kết nối! Session ID: " + session.getId() + " | Lý do: " + status);
//         sessionFrameBuffer.remove(session.getId());
//     }

//     @Override
//     protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
//         try {
//             JsonNode jsonMessage = mapper.readTree(message.getPayload());
//             String action = jsonMessage.has("action") ? jsonMessage.get("action").asText() : "STREAM"; 

//             // ==========================================
//             // LUỒNG 1: BƠM ẢNH LIÊN TỤC VÀO THÙNG CHỨA
//             // ==========================================
//             if ("STREAM".equals(action)) {
//                 String base64Frame = jsonMessage.get("frame").asText();

//                 sessionFrameBuffer.computeIfAbsent(session.getId(), k -> new ArrayList<>()).add(base64Frame);
//                 System.out.println("📡 [WS-STREAM] Nhận 1 frame. Tổng ảnh: " + sessionFrameBuffer.get(session.getId()).size());

//                 EmotionAIResponse aiRes = riskEvaluationService.detectEmotionAsync(base64Frame).get();

//                 Map<String, Object> response = new HashMap<>();
//                 response.put("type", "LIVE_RESULT");
//                 response.put("emotion", aiRes != null ? aiRes.getEmotion() : "UNKNOWN");
//                 response.put("status", "SUCCESS");

//                 session.sendMessage(new TextMessage(mapper.writeValueAsString(response)));
//             } 
            
//             // ==========================================
//             // LUỒNG 2: CHỐT SỔ ATOMIC (ALL-IN-ONE)
//             // ==========================================
//             else if ("FINALIZE".equals(action)) {
//                 Long transactionId = jsonMessage.get("transactionId").asLong();
                
//                 // 🚀 LẤY THÊM CHUỖI AUDIO BASE64 TỪ REACT GỬI LÊN
//                 String audioBase64 = jsonMessage.has("audioBase64") ? jsonMessage.get("audioBase64").asText() : "";
//                 List<String> frames = sessionFrameBuffer.getOrDefault(session.getId(), new ArrayList<>());
                
//                 System.out.println("🔥 [WS-FINALIZE] Bắt đầu chốt sổ Atomic cho TxID: " + transactionId);

//                 try {
//                     Transaction tx = transactionRepository.findByIdWithUserSecurity(transactionId)
//                             .orElseThrow(() -> new RuntimeException("Giao dịch không tồn tại!"));
//                     String username = tx.getFromAccount().getUser().getUsername();

//                     // 🚀🚀 TÁCH 2 LUỒNG CHẠY SONG SONG (MULTI-THREADING) 🚀🚀
                    
//                     // Task 1: Check 50 ảnh khuôn mặt + cảm xúc
//                     CompletableFuture<Boolean> faceTask = CompletableFuture.supplyAsync(() -> 
//                         faceActionStrategy.validateFaceAndEmotion(tx, frames)
//                     );

//                     // Task 2: Dịch Audio Base64 ra chữ số -> So sánh với Redis
//                     CompletableFuture<Boolean> voiceTask = riskEvaluationService.verifyVoiceLivenessBase64Async(audioBase64)
//                         .thenApply(recognizedCode -> {
//                             if (recognizedCode == null || recognizedCode.trim().isEmpty()) {
//                                 auditLogService.logAction(username, "VOICE_FAILED", "Không nghe rõ OTP.");
//                                 return false;
//                             }
//                             boolean isMatch = otpService.verifyOtp(tx.getId(), recognizedCode);
//                             if (!isMatch) {
//                                 auditLogService.logAction(username, "VOICE_FAILED", "Đọc sai OTP: " + recognizedCode);
//                             }
//                             return isMatch;
//                         });

//                     // Ép CPU chờ cả 2 Task chạy xong mới đi tiếp
//                     CompletableFuture.allOf(faceTask, voiceTask).join();

//                     // Thu hoạch kết quả
//                     boolean isFaceSecure = faceTask.get();
//                     boolean isVoiceSecure = voiceTask.get();

//                     Map<String, Object> finalResponse = new HashMap<>();
//                     finalResponse.put("type", "FINAL_RESULT"); 

//                     // NẾU CẢ 2 ĐỀU PASS VÀ THÀNH CÔNG
//                     if (isFaceSecure && isVoiceSecure) {
//                         tx.setFailedAiAttempts(0);
                        
//                         // Đóng mộc trừ tiền
//                         Transaction completedTx = transactionService.executeTransactionCore(tx);
//                         auditLogService.logAction(username, "TX_SUCCESS", "Chuyển tiền thành công (Atomic: Face AI + Voice).");
                        
//                         finalResponse.put("status", "SUCCESS");
//                         finalResponse.put("message", "Xác thực Sinh Trắc Học Kép thành công!");
//                         finalResponse.put("data", completedTx); // Gửi data về để React chuyển sang trang Result
//                     } 
//                     // NẾU 1 TRONG 2 BỊ TẠCH
//                     else {
//                         int currentAttempts = tx.getFailedAiAttempts() != null ? tx.getFailedAiAttempts() : 0;
//                         currentAttempts++;
//                         tx.setFailedAiAttempts(currentAttempts);
                        
//                         if (currentAttempts >= 3) {
//                             tx.setStatus("BLOCKED");
//                             transactionRepository.save(tx);
//                             finalResponse.put("status", "ERROR");
//                             finalResponse.put("message", "Giao dịch bị hủy do xác thực sinh trắc học sai quá 3 lần!");
//                         } else {
//                             transactionRepository.save(tx);
//                             int remaining = 3 - currentAttempts;
//                             String errorMsg = !isFaceSecure ? "Khuôn mặt/cảm xúc không hợp lệ." : "Giọng nói/Mã OTP không khớp.";
//                             finalResponse.put("status", "ERROR");
//                             finalResponse.put("message", errorMsg + " Bạn còn " + remaining + " lần thử.");
//                         }
//                     }

//                     session.sendMessage(new TextMessage(mapper.writeValueAsString(finalResponse)));

//                 } finally {
//                     sessionFrameBuffer.remove(session.getId());
//                 }
//             }
//         } catch (Exception e) {
//             System.err.println("❌ Lỗi luồng WebSocket: " + e.getMessage());
//             e.printStackTrace(); 
            
//             Map<String, Object> errorRes = new HashMap<>();
//             errorRes.put("type", "FINAL_RESULT");
//             errorRes.put("status", "ERROR");
//             errorRes.put("message", "Lỗi Server: " + e.getMessage());
//             session.sendMessage(new TextMessage(mapper.writeValueAsString(errorRes)));
//         }
//     }
// }

 

package com.datn.finrisk.web.websocket;

import com.datn.finrisk.application.dtos.EmotionAIResponse;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.AuditLogService;
import com.datn.finrisk.core.services.OtpService;
import com.datn.finrisk.core.services.RiskEvaluationService;
import com.datn.finrisk.core.services.TransactionService;
import com.datn.finrisk.core.strategies.AdvancedFaceActionStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveEmotionWebSocketHandler extends TextWebSocketHandler {

    @Autowired private RiskEvaluationService riskEvaluationService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AdvancedFaceActionStrategy faceActionStrategy;
    @Autowired private OtpService otpService;
    @Autowired private TransactionService transactionService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private ObjectMapper mapper;

    private final Map<String, List<String>> sessionFrameBuffer = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToTxKey = new ConcurrentHashMap<>();  
    private final Map<String, Long> bufferCreatedAt = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("🔌 [WS] Client kết nối: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("🔌 [WS] Client ngắt kết nối: " + session.getId() + " | " + status);
        String txKey = sessionToTxKey.remove(session.getId());
        if (txKey != null) {
            sessionFrameBuffer.remove(txKey);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode json = mapper.readTree(message.getPayload());
            String action = json.has("action") ? json.get("action").asText() : "STREAM";

            // ==========================================
            // LUỒNG 1: STREAM LIVE
            // ==========================================
            if ("STREAM".equals(action)) {
                String base64Frame = json.get("frame").asText();
                String txKey = json.has("transactionId")
                    ? json.get("transactionId").asText()
                    : session.getId();

                // Lưu mapping để cleanup khi disconnect
                sessionToTxKey.put(session.getId(), txKey);
                List<String> buffer = sessionFrameBuffer.computeIfAbsent(txKey, k -> {bufferCreatedAt.put(txKey, System.currentTimeMillis()); 
                     return Collections.synchronizedList(new ArrayList<>());
                });
                if (buffer.size() < 60) {
                    buffer.add(base64Frame);
                } else {
                    System.out.println("⚠️ [STREAM] Buffer đầy, bỏ frame thừa. txKey=" + txKey);
                }
                
                System.out.println("📡 [STREAM] txKey=" + txKey + " | Frame #" + sessionFrameBuffer.get(txKey).size());

                //  Non-blocking
                riskEvaluationService.detectEmotionAsync(base64Frame)
                    .thenAccept(aiRes -> {
                        try {
                            if (!session.isOpen()) return; // ← THÊM DÒNG NÀY, bỏ qua nếu session đã đóng
                            Map<String, Object> res = new HashMap<>();
                            res.put("type", "LIVE_RESULT");
                            res.put("emotion", aiRes != null ? aiRes.getEmotion() : "UNKNOWN");
                            res.put("status", "SUCCESS");
                            synchronized (session) {
                                if (session.isOpen()) { // ← check lần 2 trong synchronized để tránh race
                                    session.sendMessage(new TextMessage(mapper.writeValueAsString(res)));
                                }
                            }
                        } catch (Exception e) {
                            System.err.println(" Lỗi gửi LIVE_RESULT: " + e.getMessage());
                        }
                    });
            }

            // ==========================================
            // LUỒNG 2: FINALIZE
            // ==========================================
            else if ("FINALIZE".equals(action)) {
                Long transactionId = json.get("transactionId").asLong();
                String txKey = String.valueOf(transactionId); // ← khớp với STREAM
                String audioBase64 = json.has("audioBase64") ? json.get("audioBase64").asText() : "";
                
                List<String> frames = new ArrayList<>(
                    sessionFrameBuffer.getOrDefault(txKey, new ArrayList<>())
                );
                System.out.println("🔥 [FINALIZE] TxID=" + transactionId + " | Frames=" + frames.size());
 
                System.out.println("🔍 [DEBUG] txKey tìm: " + txKey);
                System.out.println("🔍 [DEBUG] Tất cả key trong buffer: " + sessionFrameBuffer.keySet());
                System.out.println("🔍 [DEBUG] Số frame lấy được: " + frames.size());
                try {
                    Transaction tx = transactionRepository.findByIdWithUserSecurity(transactionId)
                        .orElseThrow(() -> new RuntimeException("Giao dịch không tồn tại!"));
                    String username = tx.getFromAccount().getUser().getUsername();

                    CompletableFuture<Boolean> faceTask = CompletableFuture.supplyAsync(() -> {
                        System.out.println("🏃 [Worker-Face] Bắt đầu...");
                        boolean result = faceActionStrategy.validateFaceAndEmotion(tx, frames);
                        System.out.println("🏁 [Worker-Face] Xong! Kết quả: " + result);
                        return result;
                    });

                    CompletableFuture<Boolean> voiceTask = riskEvaluationService
                        .verifyVoiceLivenessBase64Async(audioBase64)
                        .thenApply(recognizedCode -> {
                            System.out.println("🏃 [Worker-Voice] Nhận diện được: " + recognizedCode);
                            if (recognizedCode == null || recognizedCode.trim().isEmpty()) {
                                auditLogService.logAction(username, "VOICE_FAILED", "Không nghe rõ OTP.");
                                return false;
                            }
                            boolean isMatch = otpService.verifyOtp(tx.getId(), recognizedCode);
                            System.out.println("🏁 [Worker-Voice] OTP match: " + isMatch);
                            if (!isMatch) auditLogService.logAction(username, "VOICE_FAILED", "Sai OTP: " + recognizedCode);
                            return isMatch;
                        });

                    // Chờ cả 2 xong
                    boolean isFaceSecure = faceTask.join();
                    boolean isVoiceSecure = voiceTask.join();

                    Map<String, Object> finalRes = new HashMap<>();
                    finalRes.put("type", "FINAL_RESULT");

                    if (isFaceSecure && isVoiceSecure) {
                        tx.setFailedAiAttempts(0);
                        Transaction completedTx = transactionService.executeTransactionCore(tx);
                        auditLogService.logAction(username, "TX_SUCCESS", "Atomic: Face + Voice OK.");
                        finalRes.put("status", "SUCCESS");
                        finalRes.put("message", "Xác thực Sinh Trắc Học Kép thành công!");
                        finalRes.put("data", completedTx);
                    } else {
                        int attempts = (tx.getFailedAiAttempts() != null ? tx.getFailedAiAttempts() : 0) + 1;
                        tx.setFailedAiAttempts(attempts);
                        if (attempts >= 3) {
                            tx.setStatus("BLOCKED");
                            transactionRepository.save(tx);
                            finalRes.put("status", "ERROR");
                            finalRes.put("message", "Giao dịch bị hủy do sai quá 3 lần!");
                        } else {
                            transactionRepository.save(tx);
                            String errMsg = !isFaceSecure 
                                ? "Khuôn mặt/cảm xúc không hợp lệ." 
                                : "Giọng nói/Mã OTP không khớp.";
                            finalRes.put("status", "ERROR");
                            finalRes.put("message", errMsg + " Còn " + (3 - attempts) + " lần thử.");
                        }
                    }

                    synchronized (session) {
                        session.sendMessage(new TextMessage(mapper.writeValueAsString(finalRes)));
                    }

                } finally {
                    sessionFrameBuffer.remove(txKey);
                    sessionToTxKey.remove(session.getId());
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Lỗi WebSocket: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> errRes = new HashMap<>();
            errRes.put("type", "FINAL_RESULT");
            errRes.put("status", "ERROR");
            errRes.put("message", "Lỗi Server: " + e.getMessage());
            synchronized (session) {
                session.sendMessage(new TextMessage(mapper.writeValueAsString(errRes)));
            }
        }
    }
    @jakarta.annotation.PostConstruct
    public void startCleanupScheduler() {
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(() -> {
                long now = System.currentTimeMillis();
                long TTL_MS = 10 * 60 * 1000; // 10 phút
                bufferCreatedAt.entrySet().removeIf(entry -> {
                    if (now - entry.getValue() > TTL_MS) {
                        sessionFrameBuffer.remove(entry.getKey());
                        System.out.println("🧹 [CLEANUP] Xóa buffer hết hạn: " + entry.getKey());
                        return true;
                    }
                    return false;
                });
            }, 5, 5, java.util.concurrent.TimeUnit.MINUTES);
    }
}