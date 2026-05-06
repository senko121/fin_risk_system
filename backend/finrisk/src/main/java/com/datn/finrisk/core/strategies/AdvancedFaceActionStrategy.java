
// package com.datn.finrisk.core.strategies;

// import com.datn.finrisk.application.dtos.FaceAIResponse;
// import com.datn.finrisk.application.dtos.EmotionAIResponse; // Import DTO mới
// import com.datn.finrisk.core.entities.Transaction;
// import com.datn.finrisk.core.entities.User;
// import com.datn.finrisk.core.repository.TransactionRepository;
// import com.datn.finrisk.core.services.RiskEvaluationService;
// import com.datn.finrisk.core.services.AiAuditService; // Import điệp viên ghi log
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;

// import java.util.concurrent.CompletableFuture;

// @Service("advancedFaceActionStrategy") 
// public class AdvancedFaceActionStrategy implements RiskActionStrategy {

//     @Autowired private TransactionRepository transactionRepository;
//     @Autowired private RiskEvaluationService riskEvaluationService;
//     @Autowired private AiAuditService aiAuditService; // 🚀 Bơm Service ghi log vào

//     @Override
//     public Transaction execute(Transaction tx) {
//         System.out.println("THỰC THI CHIẾN THUẬT: ADVANCED_FACE_ACTION (HIGH)");
//         tx.setRiskLevel("HIGH");
//         tx.setStatus("PENDING_PIN_HIGH"); 
//         return transactionRepository.save(tx);
//     }
    
//     public boolean validateFaceAndEmotion(Transaction tx, String liveImageBase64) {
//         User user = tx.getFromAccount().getUser();
//         String registeredImage = user.getBase64FaceImage(); 

//         if (registeredImage == null || registeredImage.isEmpty()) {
//             System.err.println("❌ Lỗi: Người dùng chưa đăng ký khuôn mặt gốc!");
//             return false;
//         }

//         System.out.println("🚀 ĐANG GỌI SONG SONG 2 SERVICE AI (PORT 5000 & 5001)...");

//         CompletableFuture<FaceAIResponse> identityTask = 
//             riskEvaluationService.verifyIdentityAsync(liveImageBase64, registeredImage);
            
//         // 🚀 ĐÃ SỬA: Hứng bằng EmotionAIResponse
//         CompletableFuture<EmotionAIResponse> emotionTask = 
//             riskEvaluationService.detectEmotionAsync(liveImageBase64);

//         try {
//             CompletableFuture.allOf(identityTask, emotionTask).join();

//             FaceAIResponse idResult = identityTask.get();
//             EmotionAIResponse emotionResult = emotionTask.get(); // Lấy nguyên cục DTO

//             // Trích xuất chữ cái để logic chạy tiếp
//             String emotion = (emotionResult != null && emotionResult.getEmotion() != null) 
//                              ? emotionResult.getEmotion().toUpperCase() : "UNKNOWN";

//             System.out.println("🔍 KẾT QUẢ AI: Identity=" + 
//                 (idResult != null && idResult.isMatched()) + " | Emotion=" + emotion);

//             tx.setEmotionSignal(emotion);

//             // 🚀 BẮN PHÁT SÚNG GHI LOG VÀO BACKGROUND (Không làm chậm luồng)
//             if (emotionResult != null) {
//                 aiAuditService.logEmotionScan(tx, emotionResult);
//             }

//             if (idResult == null || !idResult.isMatched()) {
//                 return false;
//             }

//             if ("FEAR".equals(emotion) || "STRESS".equals(emotion)) {
//                 System.out.println("🚨 PHÁT HIỆN TÂM LÝ BẤT THƯỜNG - NGHI VẤN BỊ CƯỠNG ÉP!");
//                 return false;
//             }

//             return true;

//         } catch (Exception e) {
//             System.err.println("Lỗi xử lý AI song song: " + e.getMessage());
//             return false;
//         }
//     }
// }


package com.datn.finrisk.core.strategies;

import com.datn.finrisk.application.dtos.FaceAIResponse;
import com.datn.finrisk.application.dtos.EmotionAIResponse;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.exceptions.BusinessLogicException; // 🚀 THÊM IMPORT NÀY
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.RiskEvaluationService;
import com.datn.finrisk.core.services.AiAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service("advancedFaceActionStrategy") 
public class AdvancedFaceActionStrategy implements RiskActionStrategy {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private RiskEvaluationService riskEvaluationService;
    @Autowired private AiAuditService aiAuditService;

    @Override
    public Transaction execute(Transaction tx) {
        System.out.println("THỰC THI CHIẾN THUẬT: ADVANCED_FACE_ACTION (HIGH)");
        
        // 🚀 LÔ CỐT ĐÃ ĐƯỢC DỰNG LÊN TẠI ĐÂY:
        User user = tx.getFromAccount().getUser();
        String registeredImage = user.getBase64FaceImage(); 
        if (registeredImage == null || registeredImage.trim().isEmpty()) {
            throw new BusinessLogicException("ERR_NO_FACE_SETUP", "Giao dịch rủi ro cao. Bạn chưa cài đặt FaceID, vui lòng thiết lập trước khi thực hiện!");
        }

        tx.setRiskLevel("HIGH");
        tx.setStatus("PENDING_PIN_HIGH"); 
        return transactionRepository.save(tx);
    }
    
    public boolean validateFaceAndEmotion(Transaction tx, String liveImageBase64) {
        User user = tx.getFromAccount().getUser();
        String registeredImage = user.getBase64FaceImage(); 

        if (registeredImage == null || registeredImage.isEmpty()) {
            System.err.println("❌ Lỗi: Người dùng chưa đăng ký khuôn mặt gốc!");
            return false;
        }

        System.out.println("🚀 ĐANG GỌI SONG SONG 2 SERVICE AI (PORT 5000 & 5001)...");

        CompletableFuture<FaceAIResponse> identityTask = 
            riskEvaluationService.verifyIdentityAsync(liveImageBase64, registeredImage);
            
        CompletableFuture<EmotionAIResponse> emotionTask = 
            riskEvaluationService.detectEmotionAsync(liveImageBase64);

        try {
            CompletableFuture.allOf(identityTask, emotionTask).join();

            FaceAIResponse idResult = identityTask.get();
            EmotionAIResponse emotionResult = emotionTask.get(); 

            String emotion = (emotionResult != null && emotionResult.getEmotion() != null) 
                             ? emotionResult.getEmotion().toUpperCase() : "UNKNOWN";

            System.out.println("🔍 KẾT QUẢ AI: Identity=" + 
                (idResult != null && idResult.isMatched()) + " | Emotion=" + emotion);

            tx.setEmotionSignal(emotion);

            if (emotionResult != null) {
                aiAuditService.logEmotionScan(tx, emotionResult);
            }

            if (idResult == null || !idResult.isMatched()) {
                return false;
            }

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