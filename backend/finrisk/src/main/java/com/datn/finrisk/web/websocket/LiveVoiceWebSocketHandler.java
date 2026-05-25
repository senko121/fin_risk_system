package com.datn.finrisk.web.websocket;

import com.datn.finrisk.core.entities.BiometricSession;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.OtpService;
import com.datn.finrisk.core.services.VerificationSyncManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LiveVoiceWebSocketHandler extends BinaryWebSocketHandler {

    @Autowired private VerificationSyncManager syncManager;
    @Autowired private OtpService otpService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private WsConnectionGuard wsConnectionGuard;
    @Autowired private ObjectMapper mapper;

    private final Map<String, WebSocketSession> pythonSessions = new ConcurrentHashMap<>();

    private static final int PYTHON_WS_CONNECT_TIMEOUT_SECONDS = 5;

    @Override
    public void afterConnectionEstablished(WebSocketSession reactSession) throws Exception {
        String ip = clientIp(reactSession);

        BiometricSession biometricSession = (BiometricSession) reactSession.getAttributes().get("biometricSession");
        if (biometricSession == null) {
            log.error("[WS-VOICE][SECURITY] biometricSession is null session={} ip={} — closing", reactSession.getId(), ip);
            reactSession.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        String authenticatedUser = (String) reactSession.getAttributes().get("authenticatedUser");
        if (authenticatedUser == null) {
            log.error("[WS-VOICE][SECURITY] no authenticated user session={} ip={} — closing", reactSession.getId(), ip);
            reactSession.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        String sessionOwner = biometricSession.getUser().getUsername();
        if (!authenticatedUser.equals(sessionOwner)) {
            log.error("[WS-VOICE][SECURITY] ownership violation jwt_user={} session_owner={} session={} ip={}",
                    authenticatedUser, sessionOwner, reactSession.getId(), ip);
            reactSession.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        Long txId = biometricSession.getTransaction().getId();
        String txKey = biometricSession.getSessionToken();
        String corrId = "WS-" + reactSession.getId().replace("-", "").substring(0, 8);
        reactSession.getAttributes().put("correlationId", corrId);
        MDC.put("correlationId", corrId);
        MDC.put("txId", String.valueOf(txId));
        try {
        log.info("[WS-VOICE] session={} ip={} user={} txId={} — opening bridge to Python voice service",
                reactSession.getId(), ip, authenticatedUser, txId);

        StandardWebSocketClient client = new StandardWebSocketClient();
        var pythonConnectFuture = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession pySession, TextMessage message) throws Exception {
                MDC.put("correlationId", corrId);
                MDC.put("txId", String.valueOf(txId));
                try {
                    JsonNode json = mapper.readTree(message.getPayload());
                    String type = json.get("type").asText();

                    if ("VERIFICATION_COMPLETE".equals(type)) {
                        String authCode = json.has("authCode") ? json.get("authCode").asText() : "";
                        log.info("[WS-VOICE][PYTHON] received VERIFICATION_COMPLETE txKey={} authCode_length={}",
                                txKey, authCode.length());

                        Transaction tx = transactionRepository.findByIdWithUserSecurity(txId).orElse(null);
                        String username = (tx != null) ? tx.getFromAccount().getUser().getUsername() : "unknown";

                        boolean isMatch = otpService.verifyVoiceOtp(txId, authCode);
                        log.info("[WS-VOICE] voice OTP match={} txId={}", isMatch, txId);

                        syncManager.updateVoiceResult(txKey, isMatch, username, txId, reactSession);
                    }
                } finally {
                    MDC.clear();
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession pySession, CloseStatus status) throws Exception {
                log.info("[WS-VOICE][PYTHON] Python voice service disconnected status={} — closing react session", status.getCode());
                if (reactSession.isOpen()) {
                    reactSession.close();
                }
            }
        }, "ws://localhost:5003/ws/recognize");

        WebSocketSession pythonSession;
        try {
            pythonSession = pythonConnectFuture.get(PYTHON_WS_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[WS-VOICE] timeout/error connecting to Python voice service txKey={}: {}", txKey, e.getMessage());
            if (reactSession.isOpen()) {
                reactSession.close(CloseStatus.SERVICE_OVERLOAD);
            }
            return;
        }

        pythonSessions.put(reactSession.getId(), pythonSession);
        log.info("[WS-VOICE] bridge established session={} ip={} txId={}", reactSession.getId(), ip, txId);
        } finally {
            MDC.clear();
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession reactSession, BinaryMessage message) throws Exception {
        WebSocketSession pythonSession = pythonSessions.get(reactSession.getId());
        if (pythonSession != null && pythonSession.isOpen()) {
            pythonSession.sendMessage(message);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession reactSession, Throwable error) throws Exception {
        String ip = clientIp(reactSession);
        log.error("[WS-VOICE] transport error session={} ip={} error={}", reactSession.getId(), ip, error.getMessage());
        wsConnectionGuard.release(ip);
        WebSocketSession pythonSession = pythonSessions.remove(reactSession.getId());
        if (pythonSession != null && pythonSession.isOpen()) {
            pythonSession.close(CloseStatus.SERVER_ERROR);
        }
        if (reactSession.isOpen()) {
            reactSession.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession reactSession, CloseStatus status) throws Exception {
        String ip = clientIp(reactSession);
        wsConnectionGuard.release(ip);
        log.info("[WS-VOICE] closed session={} ip={} status={}", reactSession.getId(), ip, status.getCode());
        WebSocketSession pythonSession = pythonSessions.remove(reactSession.getId());
        if (pythonSession != null && pythonSession.isOpen()) {
            try {
                pythonSession.close();
            } catch (Exception e) {
                log.warn("[WS-VOICE][PYTHON] Error closing Python bridge session={}: {}", reactSession.getId(), e.getMessage());
            }
        }
    }

    private String clientIp(WebSocketSession session) {
        Object ip = session.getAttributes().get("clientIp");
        return ip != null ? ip.toString() : "unknown";
    }
}
