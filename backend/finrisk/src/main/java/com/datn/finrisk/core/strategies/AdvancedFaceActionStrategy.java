 

package com.datn.finrisk.core.strategies;

import com.datn.finrisk.application.dtos.FaceAIResponse;
import com.datn.finrisk.application.dtos.EmotionAIResponse;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.exceptions.BusinessLogicException; // 🚀 THÊM IMPORT NÀY
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.RiskEvaluationService;
import com.datn.finrisk.core.services.AiAuditService;
import com.datn.finrisk.core.services.biometric.EmotionEvaluator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service("advancedFaceActionStrategy") 
public class AdvancedFaceActionStrategy implements RiskActionStrategy {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private RiskEvaluationService riskEvaluationService;
    @Autowired private AiAuditService aiAuditService;

    @Autowired 
    @Qualifier("batchEmotionEvaluator")
    private EmotionEvaluator emotionEvaluator;

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
    
    public boolean validateFaceAndEmotion(Transaction tx, List<String> liveImageFrames) {
        if (liveImageFrames == null || liveImageFrames.isEmpty()) {
            System.err.println("❌ Lỗi: Frontend không gửi frame ảnh nào!");
            return false;
        }

        User user = tx.getFromAccount().getUser();
        String registeredImage = user.getBase64FaceImage(); 

        if (registeredImage == null || registeredImage.isEmpty()) {
            System.err.println("❌ Lỗi: Người dùng chưa đăng ký khuôn mặt gốc!");
            return false;
        }

        System.out.println("🚀 ĐANG GỌI SONG SONG AI...");

        // 1. Xác thực khuôn mặt (Chỉ cần lấy 1 tấm ảnh NÉT NHẤT, ví dụ tấm đầu tiên)
        CompletableFuture<FaceAIResponse> identityTask = 
            riskEvaluationService.verifyIdentityAsync(liveImageFrames.get(0), registeredImage);
            
        // 2. Đánh giá chuỗi cảm xúc (Truyền toàn bộ mảng cho Evaluator mới)
        CompletableFuture<EmotionAIResponse> emotionTask = 
            emotionEvaluator.evaluateSequenceAsync(liveImageFrames);

        try {
            CompletableFuture.allOf(identityTask, emotionTask).join();

            FaceAIResponse idResult = identityTask.get();
            EmotionAIResponse emotionResult = emotionTask.get(); 

            String emotion = (emotionResult != null && emotionResult.getEmotion() != null) 
                             ? emotionResult.getEmotion().toUpperCase() : "UNKNOWN";

            System.out.println("🔍 KẾT QUẢ AI: Identity=" + 
                (idResult != null && idResult.isMatched()) + " | Aggregate Emotion=" + emotion);

            tx.setEmotionSignal(emotion);

            if (emotionResult != null) {
                aiAuditService.logEmotionScan(tx, emotionResult);
            }

            if (idResult == null || !idResult.isMatched()) {
                return false; // KHÔNG PHẢI CHỦ TÀI KHOẢN
            }

            // Xử lý logic từ chối nếu có dấu hiệu ép buộc
            if ("FEAR".equals(emotion) || "STRESS".equals(emotion)) {
                System.out.println("🚨 PHÁT HIỆN TÂM LÝ BẤT THƯỜNG - NGHI VẤN BỊ CƯỠNG ÉP TRONG SUỐT QUÁ TRÌNH!");
                return false;
            }

            return true;

        } catch (Exception e) {
            System.err.println("Lỗi xử lý AI song song: " + e.getMessage());
            return false;
        }
    }
}