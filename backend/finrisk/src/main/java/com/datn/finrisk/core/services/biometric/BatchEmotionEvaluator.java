package com.datn.finrisk.core.services.biometric;

import com.datn.finrisk.application.dtos.EmotionAIResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

 
@Service("batchEmotionEvaluator") 
public class BatchEmotionEvaluator implements EmotionEvaluator {

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Async("aiTaskExecutor")
    @Override
    public CompletableFuture<EmotionAIResponse> evaluateSequenceAsync(List<String> frames) {
        System.out.println("--- [BATCH EMOTION] Đang gửi " + frames.size() + " ảnh sang Port 5001... ---");
        try {
 
            String url = "http://localhost:5001/api/ai/detect-emotion-sequence"; 
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("image_base64_list", frames);  

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestMap, headers);
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("emotionAiService");
            EmotionAIResponse response = cb.executeSupplier(() -> restTemplate.postForObject(url, entity, EmotionAIResponse.class));

            if(response != null) {
                 System.out.println("✅ [BATCH EMOTION] Cảm xúc tổng hợp chuỗi: " + response.getEmotion());
            }
            return CompletableFuture.completedFuture(response);
        } catch (CallNotPermittedException e) {
            System.err.println("❌ [BATCH EMOTION] Circuit OPEN for emotionAiService — skipping call.");
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            System.err.println("❌ [BATCH EMOTION] LỖI: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }
}