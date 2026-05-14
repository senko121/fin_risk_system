package com.datn.finrisk.web.websocket;

import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.OtpService;
import com.datn.finrisk.core.services.VerificationSyncManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveVoiceWebSocketHandler extends BinaryWebSocketHandler {

    @Autowired private VerificationSyncManager syncManager;
    @Autowired private OtpService otpService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ObjectMapper mapper;

    // Map chứa kết nối: Session của React -> Session của Python
    private final Map<String, WebSocketSession> pythonSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession reactSession) throws Exception {
        // 1. Lấy transactionId từ URL Frontend truyền lên
        String query = reactSession.getUri().getQuery();
        String txKey = (query != null && query.contains("txId=")) ? query.split("txId=")[1] : null;

        if (txKey == null) {
            System.err.println("❌ [WS-VOICE] Đóng kết nối vì không có txId!");
            reactSession.close(CloseStatus.BAD_DATA);
            return;
        }

        System.out.println("🎤 [WS-VOICE] Client React kết nối. Đang mở cầu nối sang Python (Port 5003)...");

        // 2. MỞ KẾT NỐI SANG PYTHON VOSK
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession pythonSession = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession pySession, TextMessage message) throws Exception {
                // 4. HỨNG KẾT QUẢ TỪ PYTHON TRẢ VỀ
                JsonNode json = mapper.readTree(message.getPayload());
                String type = json.get("type").asText();

                if ("VERIFICATION_COMPLETE".equals(type)) {
                    String authCode = json.has("authCode") ? json.get("authCode").asText() : "";
                    System.out.println("🤖 [PYTHON -> JAVA] Nhận được mã OTP tổng hợp: " + authCode);

                    // Xác thực mã OTP
                    Long txId = Long.parseLong(txKey);
                    Transaction tx = transactionRepository.findByIdWithUserSecurity(txId).orElse(null);
                    String username = (tx != null) ? tx.getFromAccount().getUser().getUsername() : "Unknown";

                    boolean isMatch = otpService.verifyOtp(txId, authCode);
                    System.out.println("🏁 [Worker-Voice] OTP Match: " + isMatch);

                    // Báo cáo về Trạm Điều Phối
                    syncManager.updateVoiceResult(txKey, isMatch, username, txId);
                }
            }
            //   FIX ZOMBIE SOCKET BÊN JAVA: Python ngắt -> Java ngắt React
            @Override
            public void afterConnectionClosed(WebSocketSession pySession, CloseStatus status) throws Exception {
                System.out.println("🛑 [PYTHON] Đã ngắt kết nối. Đóng luôn luồng của React cho sạch...");
                if (reactSession.isOpen()) {
                    reactSession.close();
                }
            }
        }, "ws://localhost:5003/ws/recognize").get();

        pythonSessions.put(reactSession.getId(), pythonSession);
        System.out.println("✅ [WS-VOICE] Cầu nối Java <-> Python thành công!");
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession reactSession, BinaryMessage message) throws Exception {
        // 3. CHUYỂN PHÁT NHANH (RELAY) AUDIO
        // Cứ nhận byte nhị phân nào từ React là ném thẳng sang Python
        WebSocketSession pythonSession = pythonSessions.get(reactSession.getId());
        if (pythonSession != null && pythonSession.isOpen()) {
            pythonSession.sendMessage(message);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession reactSession, CloseStatus status) throws Exception {
        System.out.println("🛑 [WS-VOICE] Client React ngắt kết nối.");
        // Đóng luôn kết nối với Python để giải phóng RAM
        WebSocketSession pythonSession = pythonSessions.remove(reactSession.getId());
        if (pythonSession != null && pythonSession.isOpen()) {
            pythonSession.close();
        }
    }
}