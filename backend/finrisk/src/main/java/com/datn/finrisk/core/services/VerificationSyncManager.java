// package com.datn.finrisk.core.services;

// import com.datn.finrisk.core.entities.Transaction;
// import com.datn.finrisk.core.repository.TransactionRepository;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;
// import org.springframework.web.socket.TextMessage;
// import org.springframework.web.socket.WebSocketSession;

// import java.util.HashMap;
// import java.util.Map;
// import java.util.concurrent.ConcurrentHashMap;

// @Service
// public class VerificationSyncManager {

//     @Autowired private TransactionService transactionService;
//     @Autowired private AuditLogService auditLogService;
//     @Autowired private TransactionRepository transactionRepository;
//     @Autowired private ObjectMapper mapper;

//     // 🚀 Lớp cấu trúc mới: Ôm cả trạng thái đúng/sai và cái ống Mạng (Session)
//     private static class AuthState {
//         Boolean isFacePassed = null;
//         Boolean isVoicePassed = null;
//         WebSocketSession faceSession = null; 
//         String errMsg = "";
//     }

//     private final Map<String, AuthState> syncMap = new ConcurrentHashMap<>();

//     // 1. Nhận kết quả Face (Có kèm session mạng)
//     public synchronized void updateFaceResult(String txKey, boolean isPassed, String username, Long txId, WebSocketSession session) {
//         AuthState state = syncMap.computeIfAbsent(txKey, k -> new AuthState());
//         state.isFacePassed = isPassed;
//         state.faceSession = session; // Lưu cái ống mạng lại
        
//         if (!isPassed) state.errMsg = "Khuôn mặt hoặc cảm xúc không hợp lệ. ";
//         checkAndFinalize(txKey, username, txId, state);
//     }

//     // 2. Nhận kết quả Voice
//     public synchronized void updateVoiceResult(String txKey, boolean isPassed, String username, Long txId) {
//         AuthState state = syncMap.computeIfAbsent(txKey, k -> new AuthState());
//         state.isVoicePassed = isPassed;
        
//         if (!isPassed) state.errMsg += "Giọng nói hoặc Mã OTP không khớp. ";
//         checkAndFinalize(txKey, username, txId, state);
//     }

//     // 3. Trạm chốt sổ
//     private void checkAndFinalize(String txKey, String username, Long txId, AuthState state) {
//         // Đợi đủ 2 mảnh ghép Face và Voice thì mới chạy tiếp
//         if (state.isFacePassed == null || state.isVoicePassed == null) {
//             return;
//         }

//         try {
//             Transaction tx = transactionRepository.findById(txId).orElse(null);
//             if (tx == null) return;

//             Map<String, Object> finalRes = new HashMap<>();
//             finalRes.put("type", "FINAL_RESULT");

//             if (state.isFacePassed && state.isVoicePassed) {
//                 // THÀNH CÔNG RỰC RỠ
//                 tx.setFailedAiAttempts(0);
//                 Transaction completedTx = transactionService.executeTransactionCore(tx);
//                 auditLogService.logAction(username, "TX_SUCCESS", "Biometric Kép (Tách luồng): Face + Voice OK.");
                
//                 finalRes.put("status", "SUCCESS");
//                 finalRes.put("message", "Xác thực Sinh Trắc Học Kép thành công!");
//                 finalRes.put("data", completedTx);
//             } else {
//                 // THẤT BẠI 1 TRONG 2
//                 int attempts = (tx.getFailedAiAttempts() != null ? tx.getFailedAiAttempts() : 0) + 1;
//                 tx.setFailedAiAttempts(attempts);
//                 if (attempts >= 3) {
//                     tx.setStatus("BLOCKED");
//                     finalRes.put("message", "Giao dịch bị hủy do sai quá 3 lần!");
//                 } else {
//                     finalRes.put("message", state.errMsg + "Còn " + (3 - attempts) + " lần thử.");
//                 }
//                 transactionRepository.save(tx);
//                 finalRes.put("status", "ERROR");
//             }

//             // Dùng cái ống mạng đã lưu ở trên để bắn kết quả JSON về cho Frontend
//             if (state.faceSession != null && state.faceSession.isOpen()) {
//                 synchronized (state.faceSession) {
//                     state.faceSession.sendMessage(new TextMessage(mapper.writeValueAsString(finalRes)));
//                 }
//             }

//         } catch (Exception e) {
//             System.err.println("❌ Lỗi khi chốt sổ SyncManager: " + e.getMessage());
//         } finally {
//             syncMap.remove(txKey); // Dọn rác giải phóng RAM
//         }
//     }
// }







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

    // 🚀 Lớp cấu trúc: Ôm trạng thái đúng/sai và cái ống Mạng (Session)
    private static class AuthState {
        Boolean isFacePassed = null;
        Boolean isVoicePassed = null;
        WebSocketSession faceSession = null; 
        String errMsg = "";
    }

    private final Map<String, AuthState> syncMap = new ConcurrentHashMap<>();

    // 1. Nhận kết quả Face
    public synchronized void updateFaceResult(String txKey, boolean isPassed, String username, Long txId, WebSocketSession session) {
        AuthState state = syncMap.computeIfAbsent(txKey, k -> new AuthState());
        state.isFacePassed = isPassed;
        state.faceSession = session; 
        
        if (!isPassed) state.errMsg = "Khuôn mặt hoặc cảm xúc không hợp lệ. ";
        checkAndFinalize(txKey, username, txId, state);
    }

    // 2. Nhận kết quả Voice
    public synchronized void updateVoiceResult(String txKey, boolean isPassed, String username, Long txId) {
        AuthState state = syncMap.computeIfAbsent(txKey, k -> new AuthState());
        state.isVoicePassed = isPassed;
        
        if (!isPassed) state.errMsg += "Giọng nói hoặc Mã OTP không khớp. ";
        checkAndFinalize(txKey, username, txId, state);
    }

    // 3. TRẠM CHỐT SỔ (NƠI ĐÓN BẪY UNDER_REVIEW)
    private void checkAndFinalize(String txKey, String username, Long txId, AuthState state) {
        // Đợi đủ 2 mảnh ghép Face và Voice thì mới chạy tiếp
        if (state.isFacePassed == null || state.isVoicePassed == null) {
            return;
        }

        try {
            Transaction tx = transactionRepository.findById(txId).orElse(null);
            if (tx == null) return;

            Map<String, Object> finalRes = new HashMap<>();
            finalRes.put("type", "FINAL_RESULT");

            // ====================================================================
            // 🚀 BƯỚC 1: KIỂM TRA BẪY GIAM LỎNG (ƯU TIÊN CAO NHẤT)
            // ====================================================================
            if ("UNDER_REVIEW".equals(tx.getStatus())) {
                System.out.println("🛡️ [SYNC MANAGER] Bắt được giao dịch UNDER_REVIEW! Đang gửi thông báo hòa bình...");
                
                finalRes.put("status", "UNDER_REVIEW");
                // Câu thông báo này hiển thị lên màn hình, trông rất bình thường để câu giờ
                finalRes.put("message", "Giao dịch đang được hệ thống xử lý an toàn. Vui lòng giữ ứng dụng và chờ trong giây lát...");
                
                // ⛔ TUYỆT ĐỐI KHÔNG làm gì thêm: Không attempts++, không trừ tiền, không báo lỗi
            } 
            // ====================================================================
            // 🚀 BƯỚC 2: NẾU BÌNH THƯỜNG -> CHẠY LOGIC CHẤM ĐIỂM
            // ====================================================================
            else if (state.isFacePassed && state.isVoicePassed) {
                // THÀNH CÔNG RỰC RỠ
                tx.setFailedAiAttempts(0);
                Transaction completedTx = transactionService.executeTransactionCore(tx);
                auditLogService.logAction(username, "TX_SUCCESS", "Biometric Kép (Tách luồng): Face + Voice OK.");
                
                finalRes.put("status", "SUCCESS");
                finalRes.put("message", "Xác thực Sinh Trắc Học Kép thành công!");
                finalRes.put("data", completedTx);
            } else {
                // THẤT BẠI 1 TRONG 2 DO LÝ DO BÌNH THƯỜNG (Quét trượt, đọc sai số)
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

            // ====================================================================
            // 🚀 BƯỚC 3: BẮN KẾT QUẢ VỀ CHO REACTJS
            // ====================================================================
            if (state.faceSession != null && state.faceSession.isOpen()) {
                synchronized (state.faceSession) {
                    state.faceSession.sendMessage(new TextMessage(mapper.writeValueAsString(finalRes)));
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Lỗi khi chốt sổ SyncManager: " + e.getMessage());
        } finally {
            syncMap.remove(txKey); // Dọn rác giải phóng RAM
        }
    }
}