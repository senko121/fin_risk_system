 

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

// import java.util.Collections;
// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.ConcurrentHashMap;

// @Component
// public class LiveEmotionWebSocketHandler extends TextWebSocketHandler {

//     @Autowired private RiskEvaluationService riskEvaluationService;
//     @Autowired private TransactionRepository transactionRepository;
//     @Autowired private AdvancedFaceActionStrategy faceActionStrategy;
//     @Autowired private OtpService otpService;
//     @Autowired private TransactionService transactionService;
//     @Autowired private AuditLogService auditLogService;
//     @Autowired private ObjectMapper mapper;

//     private final Map<String, List<String>> sessionFrameBuffer = new ConcurrentHashMap<>();
//     private final Map<String, String> sessionToTxKey = new ConcurrentHashMap<>();  
//     private final Map<String, Long> bufferCreatedAt = new ConcurrentHashMap<>();

//     @Override
//     public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//         System.out.println("🔌 [WS] Client kết nối: " + session.getId());
//     }

//     @Override
//     public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
//         System.out.println("🔌 [WS] Client ngắt kết nối: " + session.getId() + " | " + status);
//         String txKey = sessionToTxKey.remove(session.getId());
//         if (txKey != null) {
//             sessionFrameBuffer.remove(txKey);
//         }
//     }

//     @Override
//     protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
//         try {
//             JsonNode json = mapper.readTree(message.getPayload());
//             String action = json.has("action") ? json.get("action").asText() : "STREAM";

//             // ==========================================
//             // LUỒNG 1: STREAM LIVE
//             // ==========================================
//             if ("STREAM".equals(action)) {
//                 String base64Frame = json.get("frame").asText();
//                 String txKey = json.has("transactionId")
//                     ? json.get("transactionId").asText()
//                     : session.getId();

//                 // Lưu mapping để cleanup khi disconnect
//                 sessionToTxKey.put(session.getId(), txKey);
//                 List<String> buffer = sessionFrameBuffer.computeIfAbsent(txKey, k -> {bufferCreatedAt.put(txKey, System.currentTimeMillis()); 
//                      return Collections.synchronizedList(new ArrayList<>());
//                 });
//                 if (buffer.size() < 60) {
//                     buffer.add(base64Frame);
//                 } else {
//                     System.out.println("⚠️ [STREAM] Buffer đầy, bỏ frame thừa. txKey=" + txKey);
//                 }
                
//                 System.out.println("📡 [STREAM] txKey=" + txKey + " | Frame #" + sessionFrameBuffer.get(txKey).size());

//                 //  Non-blocking
//                 riskEvaluationService.detectEmotionAsync(base64Frame)
//                     .thenAccept(aiRes -> {
//                         try {
//                             if (!session.isOpen()) return; // ← THÊM DÒNG NÀY, bỏ qua nếu session đã đóng
//                             Map<String, Object> res = new HashMap<>();
//                             res.put("type", "LIVE_RESULT");
//                             res.put("emotion", aiRes != null ? aiRes.getEmotion() : "UNKNOWN");
//                             res.put("status", "SUCCESS");
//                             synchronized (session) {
//                                 if (session.isOpen()) { // ← check lần 2 trong synchronized để tránh race
//                                     session.sendMessage(new TextMessage(mapper.writeValueAsString(res)));
//                                 }
//                             }
//                         } catch (Exception e) {
//                             System.err.println(" Lỗi gửi LIVE_RESULT: " + e.getMessage());
//                         }
//                     });
//             }

//             // ==========================================
//             // LUỒNG 2: FINALIZE
//             // ==========================================
//             else if ("FINALIZE".equals(action)) {
//                 Long transactionId = json.get("transactionId").asLong();
//                 String txKey = String.valueOf(transactionId); // ← khớp với STREAM
//                 String audioBase64 = json.has("audioBase64") ? json.get("audioBase64").asText() : "";
                
//                 List<String> frames = new ArrayList<>(
//                     sessionFrameBuffer.getOrDefault(txKey, new ArrayList<>())
//                 );
//                 System.out.println("🔥 [FINALIZE] TxID=" + transactionId + " | Frames=" + frames.size());
 
//                 System.out.println("🔍 [DEBUG] txKey tìm: " + txKey);
//                 System.out.println("🔍 [DEBUG] Tất cả key trong buffer: " + sessionFrameBuffer.keySet());
//                 System.out.println("🔍 [DEBUG] Số frame lấy được: " + frames.size());
//                 try {
//                     Transaction tx = transactionRepository.findByIdWithUserSecurity(transactionId)
//                         .orElseThrow(() -> new RuntimeException("Giao dịch không tồn tại!"));
//                     String username = tx.getFromAccount().getUser().getUsername();

//                     CompletableFuture<Boolean> faceTask = CompletableFuture.supplyAsync(() -> {
//                         System.out.println("🏃 [Worker-Face] Bắt đầu...");
//                         boolean result = faceActionStrategy.validateFaceAndEmotion(tx, frames);
//                         System.out.println("🏁 [Worker-Face] Xong! Kết quả: " + result);
//                         return result;
//                     });

//                     CompletableFuture<Boolean> voiceTask = riskEvaluationService
//                         .verifyVoiceLivenessBase64Async(audioBase64)
//                         .thenApply(recognizedCode -> {
//                             System.out.println("🏃 [Worker-Voice] Nhận diện được: " + recognizedCode);
//                             if (recognizedCode == null || recognizedCode.trim().isEmpty()) {
//                                 auditLogService.logAction(username, "VOICE_FAILED", "Không nghe rõ OTP.");
//                                 return false;
//                             }
//                             boolean isMatch = otpService.verifyOtp(tx.getId(), recognizedCode);
//                             System.out.println("🏁 [Worker-Voice] OTP match: " + isMatch);
//                             if (!isMatch) auditLogService.logAction(username, "VOICE_FAILED", "Sai OTP: " + recognizedCode);
//                             return isMatch;
//                         });

//                     // Chờ cả 2 xong
//                     boolean isFaceSecure = faceTask.join();
//                     boolean isVoiceSecure = voiceTask.join();

//                     Map<String, Object> finalRes = new HashMap<>();
//                     finalRes.put("type", "FINAL_RESULT");

//                     if (isFaceSecure && isVoiceSecure) {
//                         tx.setFailedAiAttempts(0);
//                         Transaction completedTx = transactionService.executeTransactionCore(tx);
//                         auditLogService.logAction(username, "TX_SUCCESS", "Atomic: Face + Voice OK.");
//                         finalRes.put("status", "SUCCESS");
//                         finalRes.put("message", "Xác thực Sinh Trắc Học Kép thành công!");
//                         finalRes.put("data", completedTx);
//                     } else {
//                         int attempts = (tx.getFailedAiAttempts() != null ? tx.getFailedAiAttempts() : 0) + 1;
//                         tx.setFailedAiAttempts(attempts);
//                         if (attempts >= 3) {
//                             tx.setStatus("BLOCKED");
//                             transactionRepository.save(tx);
//                             finalRes.put("status", "ERROR");
//                             finalRes.put("message", "Giao dịch bị hủy do sai quá 3 lần!");
//                         } else {
//                             transactionRepository.save(tx);
//                             String errMsg = !isFaceSecure 
//                                 ? "Khuôn mặt/cảm xúc không hợp lệ." 
//                                 : "Giọng nói/Mã OTP không khớp.";
//                             finalRes.put("status", "ERROR");
//                             finalRes.put("message", errMsg + " Còn " + (3 - attempts) + " lần thử.");
//                         }
//                     }

//                     synchronized (session) {
//                         session.sendMessage(new TextMessage(mapper.writeValueAsString(finalRes)));
//                     }

//                 } finally {
//                     sessionFrameBuffer.remove(txKey);
//                     sessionToTxKey.remove(session.getId());
//                 }
//             }

//         } catch (Exception e) {
//             System.err.println("❌ Lỗi WebSocket: " + e.getMessage());
//             e.printStackTrace();
//             Map<String, Object> errRes = new HashMap<>();
//             errRes.put("type", "FINAL_RESULT");
//             errRes.put("status", "ERROR");
//             errRes.put("message", "Lỗi Server: " + e.getMessage());
//             synchronized (session) {
//                 session.sendMessage(new TextMessage(mapper.writeValueAsString(errRes)));
//             }
//         }
//     }
//     @jakarta.annotation.PostConstruct
//     public void startCleanupScheduler() {
//         java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
//             .scheduleAtFixedRate(() -> {
//                 long now = System.currentTimeMillis();
//                 long TTL_MS = 10 * 60 * 1000; // 10 phút
//                 bufferCreatedAt.entrySet().removeIf(entry -> {
//                     if (now - entry.getValue() > TTL_MS) {
//                         sessionFrameBuffer.remove(entry.getKey());
//                         System.out.println("🧹 [CLEANUP] Xóa buffer hết hạn: " + entry.getKey());
//                         return true;
//                     }
//                     return false;
//                 });
//             }, 5, 5, java.util.concurrent.TimeUnit.MINUTES);
//     }
// }


package com.datn.finrisk.web.websocket;

import com.datn.finrisk.application.dtos.EmotionAIResponse;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.RiskEvaluationService;
import com.datn.finrisk.core.services.VerificationSyncManager;
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
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveEmotionWebSocketHandler extends TextWebSocketHandler {

    @Autowired private RiskEvaluationService riskEvaluationService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AdvancedFaceActionStrategy faceActionStrategy;
    @Autowired private VerificationSyncManager verificationSyncManager; // 🔥 Đã thêm Trạm Điều Phối
    @Autowired private ObjectMapper mapper;

    private final Map<String, List<String>> sessionFrameBuffer = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToTxKey = new ConcurrentHashMap<>();  
    private final Map<String, Long> bufferCreatedAt = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("🔌 [WS-FACE] Client kết nối: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("🔌 [WS-FACE] Client ngắt kết nối: " + session.getId() + " | " + status);
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
            // LUỒNG 1: STREAM LIVE (GIỮ NGUYÊN KHÔNG ĐỔI)
            // ==========================================
            if ("STREAM".equals(action)) {
                String base64Frame = json.get("frame").asText();
                String txKey = json.has("transactionId") ? json.get("transactionId").asText() : session.getId();

                sessionToTxKey.put(session.getId(), txKey);
                List<String> buffer = sessionFrameBuffer.computeIfAbsent(txKey, k -> {
                     bufferCreatedAt.put(txKey, System.currentTimeMillis()); 
                     return Collections.synchronizedList(new ArrayList<>());
                });
                
                if (buffer.size() < 60) buffer.add(base64Frame);
                
                riskEvaluationService.detectEmotionAsync(base64Frame).thenAccept(aiRes -> {
                    try {
                        if (!session.isOpen()) return;
                        Map<String, Object> res = new HashMap<>();
                        res.put("type", "LIVE_RESULT");
                        res.put("emotion", aiRes != null ? aiRes.getEmotion() : "UNKNOWN");
                        res.put("status", "SUCCESS");
                        synchronized (session) {
                            if (session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(res)));
                        }
                    } catch (Exception e) {
                        System.err.println(" Lỗi gửi LIVE_RESULT: " + e.getMessage());
                    }
                });
            }

            // ==========================================
            // LUỒNG 2: FINALIZE (ĐÃ CẮT BỎ AUDIO)
            // ==========================================
            else if ("FINALIZE".equals(action)) {
                Long transactionId = json.get("transactionId").asLong();
                String txKey = String.valueOf(transactionId); 
                
                List<String> frames = new ArrayList<>(sessionFrameBuffer.getOrDefault(txKey, new ArrayList<>()));
                System.out.println("🔥 [FINALIZE FACE] TxID=" + transactionId + " | Frames=" + frames.size());

                try {
                    Transaction tx = transactionRepository.findByIdWithUserSecurity(transactionId)
                        .orElseThrow(() -> new RuntimeException("Giao dịch không tồn tại!"));
                    String username = tx.getFromAccount().getUser().getUsername();

                    // 🚀 CHỈ CHẠY FACE VALIDATION
                    System.out.println("🏃 [Worker-Face] Bắt đầu...");
                    boolean isFaceSecure = faceActionStrategy.validateFaceAndEmotion(tx, frames);
                    System.out.println("🏁 [Worker-Face] Xong! Kết quả: " + isFaceSecure);

                    // 🚀 NÉM KẾT QUẢ CHO TRẠM ĐIỀU PHỐI (Kèm theo session để nó trả JSON về Frontend)
                    verificationSyncManager.updateFaceResult(txKey, isFaceSecure, username, transactionId, session);

                } finally {
                    sessionFrameBuffer.remove(txKey);
                    sessionToTxKey.remove(session.getId());
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Lỗi WebSocket Face: " + e.getMessage());
            Map<String, Object> errRes = new HashMap<>();
            errRes.put("type", "FINAL_RESULT");
            errRes.put("status", "ERROR");
            errRes.put("message", "Lỗi Server Face: " + e.getMessage());
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(errRes)));
            }
        }
    }

    @jakarta.annotation.PostConstruct
    public void startCleanupScheduler() {
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(() -> {
                long now = System.currentTimeMillis();
                long TTL_MS = 10 * 60 * 1000;
                bufferCreatedAt.entrySet().removeIf(entry -> {
                    if (now - entry.getValue() > TTL_MS) {
                        sessionFrameBuffer.remove(entry.getKey());
                        return true;
                    }
                    return false;
                });
            }, 5, 5, java.util.concurrent.TimeUnit.MINUTES);
    }
}