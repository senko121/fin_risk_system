package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.EmotionAIResponse;
import com.datn.finrisk.core.entities.AiScanLog;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.AiScanLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class AiAuditService {

    @Autowired private AiScanLogRepository aiScanLogRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Hàm này chạy trên Luồng (Thread) riêng biệt nhờ @Async
    @Async("aiTaskExecutor")
    public void logEmotionScan(Transaction tx, EmotionAIResponse response) {
        try {
            AiScanLog log = new AiScanLog();
            log.setTransactionId(tx.getId());
            log.setUserId(tx.getFromAccount().getUser().getId());
            log.setScanType("EMOTION_FACE");
            log.setResultLabel(response.getEmotion());
            log.setConfidenceScore(response.getConfidence());
            log.setProcessTimeMs(response.getProcessTimeMs());
            
            // Biến cái Map 7 cảm xúc thành chuỗi JSON
            String detailsJson = objectMapper.writeValueAsString(response.getProbDetails());
            log.setEmotionDetails(detailsJson);
            
            log.setCreatedAt(LocalDateTime.now());

            aiScanLogRepo.save(log);
            System.out.println("📝 [AUDIT] Đã ghi log nhận diện cảm xúc vào DB thành công ngầm!");
        } catch (Exception e) {
            System.err.println("❌ [AUDIT] Lỗi ghi log AI: " + e.getMessage());
        }
    }
}