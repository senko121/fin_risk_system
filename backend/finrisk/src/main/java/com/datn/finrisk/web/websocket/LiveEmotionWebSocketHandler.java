package com.datn.finrisk.web.websocket;

import com.datn.finrisk.core.entities.BiometricSession;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.RiskEvaluationService;
import com.datn.finrisk.core.services.VerificationSyncManager;
import com.datn.finrisk.core.strategies.AdvancedFaceActionStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import org.slf4j.MDC;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LiveEmotionWebSocketHandler extends AbstractWebSocketHandler {

    @Autowired private RiskEvaluationService riskEvaluationService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AdvancedFaceActionStrategy faceActionStrategy;
    @Autowired private VerificationSyncManager verificationSyncManager;
    @Autowired private WsConnectionGuard wsConnectionGuard;
    @Autowired private FaceSessionRegistry faceSessionRegistry;
    @Autowired private ObjectMapper mapper;

    // Binary frame validation bounds
    private static final int MIN_FRAME_BYTES = 1_024;           // 1 KB
    private static final int MAX_FRAME_BYTES = 2 * 1024 * 1024; // 2 MB
    // Per-session cap: 60 frames × 300 KB ≈ 18 MB
    private static final long MAX_SESSION_BYTES = 60L * 300 * 1024;

    private final Map<String, List<byte[]>> sessionFrameBuffer = new ConcurrentHashMap<>();
    private final Map<String, Long>         sessionByteCount   = new ConcurrentHashMap<>();
    private final Map<String, String>       sessionToTxKey     = new ConcurrentHashMap<>();
    private final Map<String, Long>         bufferCreatedAt    = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> finalizeInFlight  = new ConcurrentHashMap<>();
    private final Map<String, String>       txKeyToSessionId   = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String ip = clientIp(session);
        String corrId = "WS-" + session.getId().replace("-", "").substring(0, 8);
        session.getAttributes().put("correlationId", corrId);
        log.info("[WS-FACE] connected session={} ip={}", session.getId(), ip);

        BiometricSession bs = (BiometricSession) session.getAttributes().get("biometricSession");
        if (bs != null) {
            faceSessionRegistry.register(bs.getSessionToken(), session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String ip = clientIp(session);
        wsConnectionGuard.release(ip);

        BiometricSession bs = (BiometricSession) session.getAttributes().get("biometricSession");
        if (bs != null) {
            faceSessionRegistry.unregisterIfOwner(bs.getSessionToken(), session);
        }

        String txKey = sessionToTxKey.remove(session.getId());
        if (txKey != null) {
            if (session.getId().equals(txKeyToSessionId.get(txKey))) {
                sessionFrameBuffer.remove(txKey);
                sessionByteCount.remove(txKey);
                bufferCreatedAt.remove(txKey);
                finalizeInFlight.remove(txKey);
                txKeyToSessionId.remove(txKey);
                log.info("[WS-FACE] closed session={} ip={} status={} — buffer cleaned txKey={}",
                        session.getId(), ip, status.getCode(), txKey);
            } else {
                log.info("[WS-FACE] closed session={} ip={} status={} — buffer preserved (new session owns txKey={})",
                        session.getId(), ip, status.getCode(), txKey);
            }
        } else {
            log.info("[WS-FACE] closed session={} ip={} status={}", session.getId(), ip, status.getCode());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) throws Exception {
        String ip = clientIp(session);
        log.error("[WS-FACE] transport error session={} ip={} error={}", session.getId(), ip, error.getMessage());
        wsConnectionGuard.release(ip);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    // -------------------------------------------------------------------------
    // Binary STREAM path — raw JPEG bytes, no JSON overhead
    // -------------------------------------------------------------------------
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String corrId = (String) session.getAttributes().get("correlationId");
        if (corrId != null) MDC.put("correlationId", corrId);
        try {
            BiometricSession biometricSession = (BiometricSession) session.getAttributes().get("biometricSession");
            if (biometricSession == null) {
                log.error("[WS-FACE][BINARY][SECURITY] biometricSession null session={} — closing", session.getId());
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            String authenticatedUser = getAuthenticatedUser(session);
            if (authenticatedUser == null) {
                log.warn("[WS-FACE][BINARY][SECURITY] no authenticated user session={}", session.getId());
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }

            Long transactionId = biometricSession.getTransaction().getId();
            MDC.put("txId", String.valueOf(transactionId));
            String txKey = String.valueOf(transactionId);

            if (!Boolean.TRUE.equals(session.getAttributes().get("ownershipValidated"))) {
                if (!validateOwnership(authenticatedUser, transactionId, session, "STREAM_ERROR")) return;
                session.getAttributes().put("ownershipValidated", Boolean.TRUE);
            }

            ByteBuffer payload = message.getPayload();
            byte[] frameBytes = new byte[payload.remaining()];
            payload.get(frameBytes);
            int frameSize = frameBytes.length;

            if (frameSize < MIN_FRAME_BYTES || frameSize > MAX_FRAME_BYTES) {
                log.warn("[WS-FACE][BINARY] invalid frame size={} session={}", frameSize, session.getId());
                return;
            }
            if (!isValidJpeg(frameBytes)) {
                log.warn("[WS-FACE][BINARY] non-JPEG binary rejected session={} size={}", session.getId(), frameSize);
                return;
            }

            sessionToTxKey.put(session.getId(), txKey);
            txKeyToSessionId.put(txKey, session.getId());

            long currentBytes = sessionByteCount.getOrDefault(txKey, 0L);
            if (currentBytes + frameSize > MAX_SESSION_BYTES) {
                log.warn("[WS-FACE][BINARY] byte-cap exceeded session={} txKey={} total={}",
                        session.getId(), txKey, currentBytes + frameSize);
                return;
            }
            sessionByteCount.merge(txKey, (long) frameSize, Long::sum);

            List<byte[]> buffer = sessionFrameBuffer.computeIfAbsent(txKey, k -> {
                bufferCreatedAt.put(txKey, System.currentTimeMillis());
                return Collections.synchronizedList(new ArrayList<>());
            });

            synchronized (buffer) {
                if (buffer.size() >= 5) {
                    buffer.remove(0);
                }
                buffer.add(frameBytes);
            }

            log.debug("[WS-FACE][BUFFER] tx={} frames={} totalBytes={}", txKey, buffer.size(), sessionByteCount.get(txKey));

            String base64Frame = Base64.getEncoder().encodeToString(frameBytes);
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
                    log.warn("[WS-FACE][BINARY] failed to send LIVE_RESULT session={}: {}", session.getId(), e.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("[WS-FACE][BINARY] error session={}: {}", session.getId(), e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    // -------------------------------------------------------------------------
    // Text path — STREAM (legacy base64-in-JSON) and FINALIZE control messages
    // -------------------------------------------------------------------------
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String corrId = (String) session.getAttributes().get("correlationId");
        if (corrId != null) MDC.put("correlationId", corrId);
        try {
            JsonNode json = mapper.readTree(message.getPayload());
            String action = json.has("action") ? json.get("action").asText() : "STREAM";

            BiometricSession biometricSession = (BiometricSession) session.getAttributes().get("biometricSession");
            if (biometricSession == null) {
                log.error("[WS-FACE][SECURITY] biometricSession is null for session={} — closing", session.getId());
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }

            Long transactionId = biometricSession.getTransaction().getId();
            MDC.put("txId", String.valueOf(transactionId));
            String txKey = String.valueOf(transactionId);

            if ("STREAM".equals(action)) {
                String authenticatedUser = getAuthenticatedUser(session);
                if (authenticatedUser == null) {
                    log.warn("[WS-FACE][SECURITY] no authenticated user in session={}", session.getId());
                    session.close(CloseStatus.POLICY_VIOLATION);
                    return;
                }

                if (!Boolean.TRUE.equals(session.getAttributes().get("ownershipValidated"))) {
                    if (!validateOwnership(authenticatedUser, transactionId, session, "STREAM_ERROR")) return;
                    session.getAttributes().put("ownershipValidated", Boolean.TRUE);
                }

                JsonNode frameNode = json.get("frame");
                if (frameNode == null || frameNode.isNull()) return;

                String base64Frame = frameNode.asText();
                if (base64Frame.length() > 2_000_000) {
                    log.warn("[WS-FACE][STREAM] oversized frame rejected session={} size={}", session.getId(), base64Frame.length());
                    return;
                }

                byte[] frameBytes;
                try {
                    frameBytes = Base64.getDecoder().decode(base64Frame);
                } catch (IllegalArgumentException ex) {
                    log.warn("[WS-FACE][STREAM] invalid base64 frame session={}", session.getId());
                    return;
                }

                sessionToTxKey.put(session.getId(), txKey);
                txKeyToSessionId.put(txKey, session.getId());

                long currentBytes = sessionByteCount.getOrDefault(txKey, 0L);
                if (currentBytes + frameBytes.length > MAX_SESSION_BYTES) {
                    log.warn("[WS-FACE][STREAM] byte-cap exceeded session={} txKey={} total={}",
                            session.getId(), txKey, currentBytes + frameBytes.length);
                    return;
                }
                sessionByteCount.merge(txKey, (long) frameBytes.length, Long::sum);

                List<byte[]> buffer = sessionFrameBuffer.computeIfAbsent(txKey, k -> {
                    bufferCreatedAt.put(txKey, System.currentTimeMillis());
                    return Collections.synchronizedList(new ArrayList<>());
                });

                synchronized (buffer) {
                    if (buffer.size() >= 5) {
                        buffer.remove(0);
                    }
                    buffer.add(frameBytes);
                }

                log.debug("[WS-FACE][STREAM] session={} txKey={} bufferSize={}", session.getId(), txKey, buffer.size());

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
                        log.warn("[WS-FACE][STREAM] failed to send LIVE_RESULT session={}: {}", session.getId(), e.getMessage());
                    }
                });

            } else if ("FINALIZE".equals(action)) {
                String authenticatedUser = getAuthenticatedUser(session);
                if (authenticatedUser == null) {
                    log.warn("[WS-FACE][FINALIZE][SECURITY] no authenticated user session={}", session.getId());
                    session.close(CloseStatus.POLICY_VIOLATION);
                    return;
                }
                if (!validateOwnership(authenticatedUser, transactionId, session, "FINAL_RESULT")) return;

                AtomicBoolean inFlight = finalizeInFlight.computeIfAbsent(txKey, k -> new AtomicBoolean(false));
                if (!inFlight.compareAndSet(false, true)) {
                    log.warn("[WS-FACE][FINALIZE] duplicate FINALIZE rejected — already in-flight txKey={} session={}",
                            txKey, session.getId());
                    return;
                }

                List<byte[]> rawFrames = new ArrayList<>(sessionFrameBuffer.getOrDefault(txKey, new ArrayList<>()));
                List<String> frames = rawFrames.stream()
                        .map(Base64.getEncoder()::encodeToString)
                        .collect(Collectors.toList());

                log.info("[WS-FACE][FINALIZE] txKey={} frames={} totalBytes={} session={}",
                        txKey, frames.size(), sessionByteCount.getOrDefault(txKey, 0L), session.getId());
                if (frames.isEmpty()) log.warn("[WS-FACE][FINALIZE] txKey={} — buffer empty", txKey);

                Transaction tx;
                try {
                    tx = transactionRepository.findByIdWithUserSecurity(transactionId)
                            .orElseThrow(() -> new RuntimeException("Giao dịch không tồn tại!"));
                } catch (Exception dbEx) {
                    finalizeInFlight.remove(txKey);
                    sessionFrameBuffer.remove(txKey);
                    sessionByteCount.remove(txKey);
                    bufferCreatedAt.remove(txKey);
                    sessionToTxKey.remove(session.getId());
                    txKeyToSessionId.remove(txKey);
                    log.error("[WS-FACE][FINALIZE][DB-ERROR] txKey={} — {}", txKey, dbEx.getMessage());
                    throw dbEx;
                }

                long submitMs = System.currentTimeMillis();
                log.info("[WS-FACE][FINALIZE][ASYNC-SUBMIT] txKey={} user={} frames={}", txKey, authenticatedUser, frames.size());

                try {
                    faceActionStrategy.validateFaceAndEmotionAsync(tx, frames)
                            .whenComplete((isFaceSecure, ex) -> {
                                long elapsed = System.currentTimeMillis() - submitMs;
                                finalizeInFlight.remove(txKey);
                                sessionToTxKey.remove(session.getId());
                                if (session.getId().equals(txKeyToSessionId.get(txKey))) {
                                    sessionFrameBuffer.remove(txKey);
                                    sessionByteCount.remove(txKey);
                                    bufferCreatedAt.remove(txKey);
                                    txKeyToSessionId.remove(txKey);
                                }

                                if (ex != null) {
                                    log.error("[WS-FACE][FINALIZE][ASYNC-FAIL] txKey={} elapsed={}ms error={}",
                                            txKey, elapsed, ex.getMessage());
                                    try {
                                        Map<String, Object> errRes = new HashMap<>();
                                        errRes.put("type", "FINAL_RESULT");
                                        errRes.put("status", "ERROR");
                                        errRes.put("message", "Lỗi xác thực AI: " + ex.getMessage());
                                        synchronized (session) {
                                            if (session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(errRes)));
                                        }
                                    } catch (Exception ignored) { }
                                    return;
                                }

                                boolean result = Boolean.TRUE.equals(isFaceSecure);
                                log.info("[WS-FACE][FINALIZE][ASYNC-DONE] txKey={} elapsed={}ms result={}", txKey, elapsed, result);
                                verificationSyncManager.updateFaceResult(
                                        biometricSession.getSessionToken(), result, authenticatedUser, transactionId);
                            });
                } catch (Exception submitEx) {
                    finalizeInFlight.remove(txKey);
                    sessionFrameBuffer.remove(txKey);
                    sessionByteCount.remove(txKey);
                    bufferCreatedAt.remove(txKey);
                    sessionToTxKey.remove(session.getId());
                    txKeyToSessionId.remove(txKey);
                    throw submitEx;
                }
            }

        } catch (Exception e) {
            log.error("[WS-FACE] handleTextMessage error session={}: {}", session.getId(), e.getMessage());
            Map<String, Object> errRes = new HashMap<>();
            errRes.put("type", "FINAL_RESULT");
            errRes.put("status", "ERROR");
            errRes.put("message", "Lỗi Server Face: " + e.getMessage());
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(errRes)));
            }
        } finally {
            MDC.clear();
        }
    }

    private boolean isValidJpeg(byte[] data) {
        return data.length >= 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF;
    }

    private boolean validateOwnership(String authenticatedUser, Long transactionId,
                                      WebSocketSession session, String errorType) throws Exception {
        Transaction tx = transactionRepository.findByIdWithUserSecurity(transactionId).orElse(null);
        if (tx == null) {
            log.error("[WS-FACE][SECURITY] transaction not found txId={}", transactionId);
            return false;
        }
        String txOwner = tx.getFromAccount().getUser().getUsername();
        if (!authenticatedUser.equals(txOwner)) {
            log.error("[WS-FACE][SECURITY] ownership violation user={} txOwner={} txId={}",
                    authenticatedUser, txOwner, transactionId);
            Map<String, Object> errRes = new HashMap<>();
            errRes.put("type", errorType);
            errRes.put("code", "TX_OWNERSHIP_VIOLATION");
            errRes.put("message", "Transaction ownership validation failed");
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(errRes)));
            }
            return false;
        }
        return true;
    }

    @Scheduled(initialDelay = 5 * 60 * 1000, fixedRate = 5 * 60 * 1000)
    void cleanupStaleBuffers() {
        long now = System.currentTimeMillis();
        long TTL_MS = 10 * 60 * 1000;
        int removed = 0;
        for (var entry : bufferCreatedAt.entrySet()) {
            if (now - entry.getValue() > TTL_MS) {
                String staleKey = entry.getKey();
                sessionFrameBuffer.remove(staleKey);
                sessionByteCount.remove(staleKey);
                bufferCreatedAt.remove(staleKey);
                txKeyToSessionId.remove(staleKey);
                finalizeInFlight.remove(staleKey);
                removed++;
            }
        }
        if (removed > 0) {
            log.info("[WS-FACE][CLEANUP] evicted {} stale frame buffers (TTL={}ms)", removed, TTL_MS);
        }
    }

    private String getAuthenticatedUser(WebSocketSession session) {
        Object user = session.getAttributes().get("authenticatedUser");
        return user != null ? user.toString() : null;
    }

    private String clientIp(WebSocketSession session) {
        Object ip = session.getAttributes().get("clientIp");
        return ip != null ? ip.toString() : "unknown";
    }

}
