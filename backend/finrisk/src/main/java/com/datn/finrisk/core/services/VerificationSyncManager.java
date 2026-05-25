package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.BiometricSession;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.BiometricSessionRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
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
    @Autowired private BiometricSessionRepository biometricSessionRepository; // ĐÃ INJECT
    @Autowired private ObjectMapper mapper;

    // 🚀 THIÊU HỦY HOÀN TOÀN syncMap VÀ AuthState ĐỂ TRÁNH LEAK RAM JVM

    @Transactional
    public void updateFaceResult(String txKey, boolean isPassed, String username, Long txId, WebSocketSession session) {
        BiometricSession biometricSession = biometricSessionRepository.findBySessionTokenForUpdate(txKey).orElse(null);

        if (biometricSession == null) {
            log.warn("[SYNC][FACE] BiometricSession not found for token={}", txKey);
            return;
        }

        // Chống đúp kết quả hoặc gọi finalize 2 lần
        if (Boolean.TRUE.equals(biometricSession.getFinalized())) {
            return;
        }

        biometricSession.setFaceResult(isPassed);

        if (!isPassed) {
            String oldErr = biometricSession.getErrorMessage();
            biometricSession.setErrorMessage((oldErr != null ? oldErr : "") + "Khuôn mặt hoặc cảm xúc không hợp lệ. ");
        }

        biometricSessionRepository.save(biometricSession);

        // Gọi hàm kiểm tra và chốt hạ tập trung
        checkAndFinalize(biometricSession, username, txId, session);
    }

    @Transactional
    public void updateVoiceResult(String txKey, boolean isPassed, String username, Long txId, WebSocketSession session) {
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

        // Gọi hàm kiểm tra và chốt hạ tập trung
        checkAndFinalize(biometricSession, username, txId, session);
    }

    private void checkAndFinalize(BiometricSession biometricSession, String username, Long txId, WebSocketSession session) {
        // Defense-in-depth: guard against any future refactor that bypasses the outer finalized check
        if (Boolean.TRUE.equals(biometricSession.getFinalized())) {
            log.warn("[SYNC] checkAndFinalize: session already finalized token={} — skipping", biometricSession.getSessionToken());
            return;
        }
        // Only proceed once both AI streams have delivered their results to the DB
        if (biometricSession.getFaceResult() == null || biometricSession.getVoiceResult() == null) {
            return;
        }

        try {
            Transaction tx = transactionRepository.findById(txId).orElse(null);
            if (tx == null) return;

            // Kiểm soát trạng thái hợp lệ của giao dịch (Defense-in-depth)
            if (tx.getStatus() == null ||
                    (!tx.getStatus().startsWith("PENDING_") && !"UNDER_REVIEW".equals(tx.getStatus()))) {
                log.warn("[SYNC] tx={} status={} is not actionable — skipping finalize", txId, tx.getStatus());
                return;
            }

            Map<String, Object> finalRes = new HashMap<>();
            finalRes.put("type", "FINAL_RESULT");

            // Kịch bản 1: Bị giam lỏng do phát hiện dấu hiệu cưỡng ép (FEAR, ANGRY...)
            if ("UNDER_REVIEW".equals(tx.getStatus())) {
                log.warn("[SYNC][COERCION] tx={} UNDER_REVIEW — locking frontend, no fund movement", txId);
                
                biometricSession.setFinalized(true);
                biometricSession.setUsed(true); // Vẫn hủy vé để chặn tấn công lặp lại
                
                finalRes.put("status", "UNDER_REVIEW");
                finalRes.put("message", "Giao dịch đang được hệ thống xử lý an toàn. Vui lòng giữ ứng dụng và chờ trong giây lát...");
            } 
            // Kịch bản 2: Cả 2 luồng AI đều thành công rực rỡ
            else if (biometricSession.getFaceResult() && biometricSession.getVoiceResult()) {
                biometricSession.setFinalized(true);
                biometricSession.setUsed(true); // Xé vé

                tx.setFailedAiAttempts(0);
                Transaction completedTx = transactionService.executeTransactionCore(tx);
                auditLogService.logAction(username, "TX_SUCCESS", "Biometric Kép (DB-Synced): Face + Voice OK.");

                finalRes.put("status", "SUCCESS");
                finalRes.put("message", "Xác thực Sinh Trắc Học Kép thành công!");
                finalRes.put("data", completedTx);
            } 
            // Kịch bản 3: Thất bại (Một trong 2 hoặc cả 2 tạch)
            // Kịch bản 3: Thất bại (Một trong 2 hoặc cả 2 tạch)
            else {
                int attempts = (tx.getFailedAiAttempts() != null ? tx.getFailedAiAttempts() : 0) + 1;
                tx.setFailedAiAttempts(attempts);

                if (attempts >= 3) {
                    // HẾT LƯỢT: mới xé vé và khóa hẳn
                    biometricSession.setFinalized(true);
                    biometricSession.setUsed(true);
                    tx.setStatus("BLOCKED");
                    finalRes.put("message", "Giao dịch bị hủy do xác thực sai quá 3 lần!");
                    finalRes.put("status", "BLOCKED");
                } else {
                    // CÒN LƯỢT: reset để cho phép retry
                    biometricSession.setFinalized(false);  // ← CHƯA chốt
                    biometricSession.setUsed(false);        // ← GIỮ token sống
                    biometricSession.setFaceResult(null);   // ← reset kết quả face
                    biometricSession.setVoiceResult(null);  // ← reset kết quả voice
                    biometricSession.setExpiresAt(          // ← gia hạn thêm 5 phút
                        java.time.LocalDateTime.now().plusMinutes(5)
                    );
                    finalRes.put("message", biometricSession.getErrorMessage() 
                        + "Còn " + (3 - attempts) + " lần thử.");
                    finalRes.put("status", "RETRY");        // ← status mới để frontend hiểu
                    biometricSession.setErrorMessage(null); // ← xóa lỗi cũ cho lần sau
                }

                transactionRepository.save(tx);
            }

            // Đồng bộ trạng thái chốt sổ cuối cùng xuống DB
            biometricSessionRepository.save(biometricSession);

            // Serialize while the transaction is still open so lazy-loaded fields are accessible.
            // The actual send is deferred to afterCommit so the client never reads stale DB state.
            final String messagePayload = mapper.writeValueAsString(finalRes);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (session != null && session.isOpen()) {
                        synchronized (session) {
                            try {
                                if (session.isOpen()) {
                                    session.sendMessage(new TextMessage(messagePayload));
                                }
                            } catch (Exception ex) {
                                log.error("[SYNC] afterCommit WS send failed txId={}: {}", txId, ex.getMessage());
                            }
                        }
                    }
                }
            });

        } catch (Exception e) {
            log.error("[SYNC] Finalize failed txId={}: {}", txId, e.getMessage(), e);
        }
    }
}