// package com.datn.finrisk.core.strategies;

// import com.datn.finrisk.application.dtos.FaceAIResponse;
// import com.datn.finrisk.core.entities.Transaction;
// import com.datn.finrisk.core.entities.User;
// import com.datn.finrisk.core.repository.TransactionRepository;
// import com.datn.finrisk.core.services.RiskEvaluationService;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;

// import java.util.concurrent.CompletableFuture;

// @Service("faceScanActionStrategy")
// public class FaceScanActionStrategy implements RiskActionStrategy {

//     @Autowired private TransactionRepository transactionRepository;
//     @Autowired private RiskEvaluationService riskEvaluationService;

//     // VÒNG 1: Chuyển trạng thái khi Rule Engine quét thấy rủi ro HIGH
//     @Override
//     public Transaction execute(Transaction tx) {
//         System.out.println("⛔ THỰC THI CHIẾN THUẬT: FACE_SCAN_ACTION");
//         tx.setRiskLevel("HIGH");
//         tx.setStatus("PENDING_FACE_SCAN");
//         return transactionRepository.save(tx);
//     }

//     // VÒNG 2: Xử lý xác thực AI song song (Bất đồng bộ)
//     public boolean validateFaceAndEmotion(Transaction tx, User user, String liveImageBase64) {
//         // Lấy ảnh gốc từ trường chính xác trong Entity User của bro
//         String registeredImage = user.getBase64FaceImage(); 

//         if (registeredImage == null || registeredImage.isEmpty()) {
//             System.err.println("❌ Lỗi: Người dùng chưa đăng ký khuôn mặt gốc!");
//             return false;
//         }

//         System.out.println("🚀 ĐANG GỌI SONG SONG 2 SERVICE AI (PORT 5000 & 5001)...");

//         // Gửi 2 yêu cầu đi cùng lúc
//         CompletableFuture<FaceAIResponse> identityTask = 
//             riskEvaluationService.verifyIdentityAsync(liveImageBase64, registeredImage);
            
//         CompletableFuture<String> emotionTask = 
//             riskEvaluationService.detectEmotionAsync(liveImageBase64);

//         try {
//             // Đợi cả 2 phản hồi
//             CompletableFuture.allOf(identityTask, emotionTask).join();

//             FaceAIResponse idResult = identityTask.get();
//             String emotion = emotionTask.get();

//             System.out.println("🔍 KẾT QUẢ PHÂN TÍCH: Identity=" + 
//                 (idResult != null && idResult.isMatched()) + " | Emotion=" + emotion);

//             // Logic chặn: Sai mặt HOẶC Cảm xúc nguy hiểm
//             if (idResult == null || !idResult.isMatched()) {
//                 return false;
//             }

//             // Chặn nếu có dấu hiệu Sợ hãi/Giận dữ (Bị cưỡng ép)
//             if ("FEAR".equalsIgnoreCase(emotion) || "ANGRY".equalsIgnoreCase(emotion)) {
//                 System.out.println("🚨 PHÁT HIỆN TÂM LÝ BẤT THƯỜNG - CHẶN GD");
//                 return false;
//             }

//             return true;

//         } catch (Exception e) {
//             System.err.println("Lỗi xử lý bất đồng bộ AI: " + e.getMessage());
//             return false;
//         }
//     }
// }

package com.datn.finrisk.core.strategies;

import com.datn.finrisk.application.dtos.FaceAIResponse;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.RiskEvaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service("faceScanActionStrategy")
public class FaceScanActionStrategy implements RiskActionStrategy {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private RiskEvaluationService riskEvaluationService;

    @Override
    public Transaction execute(Transaction tx) {
        System.out.println("⛔ THỰC THI CHIẾN THUẬT: FACE_SCAN_ACTION");
        tx.setRiskLevel("HIGH");
        tx.setStatus("PENDING_FACE_SCAN");
        return transactionRepository.save(tx);
    }

    // 🚀 HÀM PHÁN QUYẾT AI BẤT ĐỒNG BỘ
    public boolean validateFaceAndEmotion(Transaction tx, String liveImageBase64) {
        User user = tx.getFromAccount().getUser();
        String registeredImage = user.getBase64FaceImage(); // Khớp với Entity của bro

        if (registeredImage == null || registeredImage.isEmpty()) {
            System.err.println("❌ Lỗi: Người dùng chưa đăng ký khuôn mặt gốc!");
            return false;
        }

        System.out.println("🚀 ĐANG GỌI SONG SONG 2 SERVICE AI (PORT 5000 & 5001)...");

        // Gửi 2 phát súng cùng lúc
        CompletableFuture<FaceAIResponse> identityTask = 
            riskEvaluationService.verifyIdentityAsync(liveImageBase64, registeredImage);
            
        CompletableFuture<String> emotionTask = 
            riskEvaluationService.detectEmotionAsync(liveImageBase64);

        try {
            // Đợi cả 2 thằng phản hồi (Máy mạnh tận dụng tối đa tại đây)
            CompletableFuture.allOf(identityTask, emotionTask).join();

            FaceAIResponse idResult = identityTask.get();
            String emotion = emotionTask.get().toUpperCase();

            System.out.println("🔍 KẾT QUẢ AI: Identity=" + 
                (idResult != null && idResult.isMatched()) + " | Emotion=" + emotion);

            // Lưu dấu vết cảm xúc vào giao dịch
            tx.setEmotionSignal(emotion);

            // Logic chặn: Sai mặt HOẶC Cảm xúc rủi ro
            if (idResult == null || !idResult.isMatched()) {
                return false;
            }

            // Chặn nếu phát hiện FEAR (Sợ hãi) hoặc STRESS
            if ("FEAR".equals(emotion) || "STRESS".equals(emotion)) {
                System.out.println("🚨 PHÁT HIỆN TÂM LÝ BẤT THƯỜNG - NGHI VẤN BỊ CƯỠNG ÉP!");
                return false;
            }

            return true;

        } catch (Exception e) {
            System.err.println("Lỗi xử lý AI song song: " + e.getMessage());
            return false;
        }
    }
}