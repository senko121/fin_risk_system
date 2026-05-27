package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.BiometricSession;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.BiometricSessionRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.web.websocket.FaceSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

@Service
public class VerificationSyncManager {

    private static final Logger log = LoggerFactory.getLogger(VerificationSyncManager.class);

    @Autowired private TransactionService transactionService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private BiometricSessionRepository biometricSessionRepository;
    @Autowired private FaceSessionRegistry faceSessionRegistry;
    @Autowired private ObjectMapper mapper;

    @Transactional
    public void updateFaceResult(String txKey, boolean isPassed, String username, Long txId) {
        BiometricSession biometricSession = biometricSessionRepository.findBySessionTokenForUpdate(txKey).orElse(null);

        if (biometricSession == null) {
            log.warn("[SYNC][FACE] BiometricSession not found for token={}", txKey);
            return;
        }

        if (Boolean.TRUE.equals(biometricSession.getFinalized())) {
            return;
        }

        biometricSession.setFaceResult(isPassed);

        if (!isPassed) {
            String oldErr = biometricSession.getErrorMessage();
            biometricSession.setErrorMessage((oldErr != null ? oldErr : "") + "Khuôn mặt hoặc cảm xúc không hợp lệ. ");
        }

        biometricSessionRepository.save(biometricSession);

        checkAndFinalize(biometricSession, username, txId);
    }

    @Transactional
    public void updateVoiceResult(String txKey, boolean isPassed, String username, Long txId) {
        BiometricSession biometricSession = biometricSessionRepository.findBySessionTokenForUpdate(txKey).orElse(null);

        if (biometricSession == null) {
            log.warn("[SYNC][VOICE] BiometricSession not found for token={}", txKey);
            return;
        }

        if (Boolean.TRUE.equals(biometricSession.getFinalized())) {
            return;
        }

        biometricSession.setVoiceResult(isPassed);

        if (!isPassed) {
            String oldErr = biometricSession.getErrorMessage();
            biometricSession.setErrorMessage((oldErr != null ? oldErr : "") + "Giọng nói hoặc Mã OTP không khớp. ");
        }

        biometricSessionRepository.save(biometricSession);

        checkAndFinalize(biometricSession, username, txId);
    }

    private void checkAndFinalize(BiometricSession biometricSession, String username, Long txId) {
        if (Boolean.TRUE.equals(biometricSession.getFinalized())) {
            log.warn("[SYNC] checkAndFinalize: session already finalized token={} — skipping", biometricSession.getSessionToken());
            return;
        }
        if (biometricSession.getFaceResult() == null || biometricSession.getVoiceResult() == null) {
            return;
        }

        // Always send via the face WebSocket — the only channel with an onmessage handler on the client.
        WebSocketSession session = faceSessionRegistry.get(biometricSession.getSessionToken()).orElse(null);

        try {
            Transaction tx = transactionRepository.findById(txId).orElse(null);
            if (tx == null) return;

            if (tx.getStatus() == null ||
                    (!tx.getStatus().startsWith("PENDING_") && !"UNDER_REVIEW".equals(tx.getStatus()))) {
                log.warn("[SYNC] tx={} status={} is not actionable — skipping finalize", txId, tx.getStatus());
                return;
            }

            Map<String, Object> finalRes = new HashMap<>();
            finalRes.put("type", "FINAL_RESULT");

            if ("UNDER_REVIEW".equals(tx.getStatus())) {
                log.warn("[SYNC][COERCION] tx={} UNDER_REVIEW — locking frontend, no fund movement", txId);

                biometricSession.setFinalized(true);
                biometricSession.setUsed(true);

                finalRes.put("status", "UNDER_REVIEW");
                finalRes.put("message", "Giao dịch đang được hệ thống xử lý an toàn. Vui lòng giữ ứng dụng và chờ trong giây lát...");
            } else if (biometricSession.getFaceResult() && biometricSession.getVoiceResult()) {
                biometricSession.setFinalized(true);
                biometricSession.setUsed(true);

                tx.setFailedAiAttempts(0);
                Transaction completedTx = transactionService.executeTransactionCore(tx);
                auditLogService.logAction(username, "TX_SUCCESS", "Biometric Kép (DB-Synced): Face + Voice OK.");

                finalRes.put("status", "SUCCESS");
                finalRes.put("message", "Xác thực Sinh Trắc Học Kép thành công!");
                finalRes.put("data", completedTx);
            } else {
                int attempts = (tx.getFailedAiAttempts() != null ? tx.getFailedAiAttempts() : 0) + 1;
                tx.setFailedAiAttempts(attempts);

                if (attempts >= 3) {
                    biometricSession.setFinalized(true);
                    biometricSession.setUsed(true);
                    tx.setStatus("BLOCKED");
                    finalRes.put("message", "Giao dịch bị hủy do xác thực sai quá 3 lần!");
                    finalRes.put("status", "BLOCKED");
                } else {
                    biometricSession.setFinalized(false);
                    biometricSession.setUsed(false);
                    biometricSession.setFaceResult(null);
                    biometricSession.setVoiceResult(null);
                    biometricSession.setExpiresAt(
                        java.time.LocalDateTime.now().plusMinutes(5)
                    );
                    finalRes.put("message", biometricSession.getErrorMessage()
                        + "Còn " + (3 - attempts) + " lần thử.");
                    finalRes.put("status", "RETRY");
                    biometricSession.setErrorMessage(null);
                }

                transactionRepository.save(tx);
            }

            biometricSessionRepository.save(biometricSession);

            final String messagePayload = mapper.writeValueAsString(finalRes);
            final WebSocketSession targetSession = session;

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (targetSession != null && targetSession.isOpen()) {
                        synchronized (targetSession) {
                            try {
                                if (targetSession.isOpen()) {
                                    targetSession.sendMessage(new TextMessage(messagePayload));
                                }
                            } catch (Exception ex) {
                                log.error("[SYNC] afterCommit WS send failed txId={}: {}", txId, ex.getMessage());
                            }
                        }
                    } else {
                        log.warn("[SYNC] afterCommit: face session closed or missing for txId={} — client must poll REST", txId);
                    }
                }
            });

        } catch (Exception e) {
            log.error("[SYNC] Finalize failed txId={}: {}", txId, e.getMessage(), e);
        }
    }
}
