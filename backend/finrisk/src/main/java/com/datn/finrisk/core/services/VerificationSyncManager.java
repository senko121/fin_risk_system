 
package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VerificationSyncManager {

    @Autowired private TransactionService transactionService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ObjectMapper mapper;

 
    private static class AuthState {
        Boolean isFacePassed = null;
        Boolean isVoicePassed = null;
        WebSocketSession faceSession = null; 
        String errMsg = "";
    }

    private final Map<String, AuthState> syncMap = new ConcurrentHashMap<>();
 
    public void updateFaceResult(String txKey, boolean isPassed, String username, Long txId, WebSocketSession session) {
        AuthState state = syncMap.computeIfAbsent(txKey, k -> new AuthState());
        synchronized (state) {
            state.isFacePassed = isPassed;
            state.faceSession = session;
            if (!isPassed) state.errMsg = "Khuôn mặt hoặc cảm xúc không hợp lệ. ";
            checkAndFinalize(txKey, username, txId, state);
        }
    }
 
    public void updateVoiceResult(String txKey, boolean isPassed, String username, Long txId) {
        AuthState state = syncMap.computeIfAbsent(txKey, k -> new AuthState());
        synchronized (state) {
            state.isVoicePassed = isPassed;
            if (!isPassed) state.errMsg += "Giọng nói hoặc Mã OTP không khớp. ";
            checkAndFinalize(txKey, username, txId, state);
        }
    }
 
    private void checkAndFinalize(String txKey, String username, Long txId, AuthState state) {
 
        if (state.isFacePassed == null || state.isVoicePassed == null) {
            return;
        }

        try {
            Transaction tx = transactionRepository.findById(txId).orElse(null);
            if (tx == null) return;

            // Defense-in-depth: reject if transaction is no longer in a pending state
            // (covers replayed WebSocket messages and races between async results)
            if (tx.getStatus() == null || !tx.getStatus().startsWith("PENDING_")) {
                System.err.println("⚠️ [SYNC] tx=" + txId + " is in status=" +
                    tx.getStatus() + " — not executable, skipping.");
                return;
            }

            Map<String, Object> finalRes = new HashMap<>();
            finalRes.put("type", "FINAL_RESULT");

 
            if ("UNDER_REVIEW".equals(tx.getStatus())) {
                System.out.println("🛡️ [SYNC MANAGER] Bắt được giao dịch UNDER_REVIEW! Đang gửi thông báo hòa bình...");
                
                finalRes.put("status", "UNDER_REVIEW");
 
                finalRes.put("message", "Giao dịch đang được hệ thống xử lý an toàn. Vui lòng giữ ứng dụng và chờ trong giây lát...");
      
            } 
 
            else if (state.isFacePassed && state.isVoicePassed) {
 
                tx.setFailedAiAttempts(0);
                Transaction completedTx = transactionService.executeTransactionCore(tx);
                auditLogService.logAction(username, "TX_SUCCESS", "Biometric Kép (Tách luồng): Face + Voice OK.");
                
                finalRes.put("status", "SUCCESS");
                finalRes.put("message", "Xác thực Sinh Trắc Học Kép thành công!");
                finalRes.put("data", completedTx);
            } else {
 
                int attempts = (tx.getFailedAiAttempts() != null ? tx.getFailedAiAttempts() : 0) + 1;
                tx.setFailedAiAttempts(attempts);
                
                if (attempts >= 3) {
                    tx.setStatus("BLOCKED");
                    finalRes.put("message", "Giao dịch bị hủy do xác thực sai quá 3 lần!");
                } else {
                    finalRes.put("message", state.errMsg + "Còn " + (3 - attempts) + " lần thử.");
                }
                
                transactionRepository.save(tx);
                finalRes.put("status", "ERROR");
            }

 
            if (state.faceSession != null && state.faceSession.isOpen()) {
                synchronized (state.faceSession) {
                    state.faceSession.sendMessage(new TextMessage(mapper.writeValueAsString(finalRes)));
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Lỗi khi chốt sổ SyncManager: " + e.getMessage());
        } finally {
            syncMap.remove(txKey); 
        }
    }
}