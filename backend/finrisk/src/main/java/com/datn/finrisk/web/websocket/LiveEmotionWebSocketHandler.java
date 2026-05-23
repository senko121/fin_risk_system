 
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
    @Autowired private VerificationSyncManager verificationSyncManager;  
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
            else if ("FINALIZE".equals(action)) {
                Long transactionId = json.get("transactionId").asLong();
                String txKey = String.valueOf(transactionId); 
                
                List<String> frames = new ArrayList<>(sessionFrameBuffer.getOrDefault(txKey, new ArrayList<>()));
                System.out.println("🔥 [FINALIZE FACE] TxID=" + transactionId + " | Frames=" + frames.size());

                try {
                    Transaction tx = transactionRepository.findByIdWithUserSecurity(transactionId)
                        .orElseThrow(() -> new RuntimeException("Giao dịch không tồn tại!"));
                    String username = tx.getFromAccount().getUser().getUsername();
 
                    System.out.println("🏃 [Worker-Face] Bắt đầu...");
                    boolean isFaceSecure = faceActionStrategy.validateFaceAndEmotion(tx, frames);
                    System.out.println("🏁 [Worker-Face] Xong! Kết quả: " + isFaceSecure);
 
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