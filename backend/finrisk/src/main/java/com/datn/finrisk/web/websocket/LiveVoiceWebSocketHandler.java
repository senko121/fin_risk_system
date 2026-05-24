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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class LiveVoiceWebSocketHandler extends BinaryWebSocketHandler {

    @Autowired private VerificationSyncManager syncManager;
    @Autowired private OtpService otpService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ObjectMapper mapper;
 
    private final Map<String, WebSocketSession> pythonSessions = new ConcurrentHashMap<>();

    // Maximum time to wait for the Python voice WebSocket handshake on localhost.
    // A healthy Python service completes the WS upgrade in < 100 ms.
    // 5 s is generous headroom; beyond this the React session is closed deterministically
    // so the user receives an immediate failure instead of an indefinite freeze.
    private static final int PYTHON_WS_CONNECT_TIMEOUT_SECONDS = 5;

    @Override
    public void afterConnectionEstablished(WebSocketSession reactSession) throws Exception { 
        String query = reactSession.getUri().getQuery();
        String txKey = (query != null && query.contains("txId=")) ? query.split("txId=")[1] : null;

        if (txKey == null) {
            System.err.println("❌ [WS-VOICE] Đóng kết nối vì không có txId!");
            reactSession.close(CloseStatus.BAD_DATA);
            return;
        }

        System.out.println("🎤 [WS-VOICE] Client React kết nối. Đang mở cầu nối sang Python (Port 5003)...");
 
        StandardWebSocketClient client = new StandardWebSocketClient();
        var pythonConnectFuture = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession pySession, TextMessage message) throws Exception {
                JsonNode json = mapper.readTree(message.getPayload());
                String type = json.get("type").asText();

                if ("VERIFICATION_COMPLETE".equals(type)) {
                    String authCode = json.has("authCode") ? json.get("authCode").asText() : "";
                    System.out.println("🤖 [PYTHON -> JAVA] Nhận được mã OTP tổng hợp: " + authCode);

                    Long txId = Long.parseLong(txKey);
                    Transaction tx = transactionRepository.findByIdWithUserSecurity(txId).orElse(null);
                    String username = (tx != null) ? tx.getFromAccount().getUser().getUsername() : "Unknown";

                    boolean isMatch = otpService.verifyVoiceOtp(txId, authCode);
                    System.out.println("🏁 [Worker-Voice] OTP Match: " + isMatch);

                    syncManager.updateVoiceResult(txKey, isMatch, username, txId);
                }
            }
            @Override
            public void afterConnectionClosed(WebSocketSession pySession, CloseStatus status) throws Exception {
                System.out.println("🛑 [PYTHON] Đã ngắt kết nối. Đóng luôn luồng của React cho sạch...");
                if (reactSession.isOpen()) {
                    reactSession.close();
                }
            }
        }, "ws://localhost:5003/ws/recognize");

        WebSocketSession pythonSession;
        try {
            pythonSession = pythonConnectFuture.get(PYTHON_WS_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.err.println("⏰ [WS-VOICE] Kết nối Python WS quá " + PYTHON_WS_CONNECT_TIMEOUT_SECONDS
                    + "s — đóng phiên React (txKey=" + txKey + ").");
            if (reactSession.isOpen()) {
                reactSession.close(CloseStatus.SERVICE_OVERLOAD);
            }
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("⚠️ [WS-VOICE] Luồng bị ngắt khi kết nối Python (txKey=" + txKey + ").");
            if (reactSession.isOpen()) {
                reactSession.close(CloseStatus.SERVER_ERROR);
            }
            return;
        }

        pythonSessions.put(reactSession.getId(), pythonSession);
        System.out.println("✅ [WS-VOICE] Cầu nối Java <-> Python thành công!");
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession reactSession, BinaryMessage message) throws Exception { 
        WebSocketSession pythonSession = pythonSessions.get(reactSession.getId());
        if (pythonSession != null && pythonSession.isOpen()) {
            pythonSession.sendMessage(message);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession reactSession, CloseStatus status) throws Exception {
        System.out.println("🛑 [WS-VOICE] Client React ngắt kết nối."); 
        WebSocketSession pythonSession = pythonSessions.remove(reactSession.getId());
        if (pythonSession != null && pythonSession.isOpen()) {
            pythonSession.close();
        }
    }
}