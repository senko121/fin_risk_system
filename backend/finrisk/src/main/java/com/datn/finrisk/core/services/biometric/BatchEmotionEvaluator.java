package com.datn.finrisk.core.services.biometric;

import com.datn.finrisk.application.dtos.EmotionAIResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service("batchEmotionEvaluator")
public class BatchEmotionEvaluator implements EmotionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(BatchEmotionEvaluator.class);

    @Value("${ai.service.emotion-sequence-url:http://localhost:5001/api/ai/detect-emotion-sequence}")
    private String emotionSequenceUrl;

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    // Runs on biometricVerifyExecutor — paired with verifyIdentityAsync for FINALIZE.
    // Both run in parallel on the same dedicated pool, isolated from STREAM load.
    @Async("biometricVerifyExecutor")
    @Override
    public CompletableFuture<EmotionAIResponse> evaluateSequenceAsync(List<String> frames) {
        long startMs = System.currentTimeMillis();
        log.info("[BATCH-EMOTION][START] frames={} thread={}", frames.size(), Thread.currentThread().getName());
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("image_base64_list", frames);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestMap, headers);
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("emotionAiService");
            EmotionAIResponse response = cb.executeSupplier(
                () -> restTemplate.postForObject(emotionSequenceUrl, entity, EmotionAIResponse.class));

            long elapsed = System.currentTimeMillis() - startMs;
            if (response != null) {
                log.info("[BATCH-EMOTION][DONE] elapsed={}ms emotion={}", elapsed, response.getEmotion());
            } else {
                log.warn("[BATCH-EMOTION][DONE] elapsed={}ms — null response from Python", elapsed);
            }
            if (elapsed > 12_000) {
                log.warn("[BATCH-EMOTION][SLOW] elapsed={}ms exceeds 12s — biometricVerifyExecutor may be under pressure", elapsed);
            }
            return CompletableFuture.completedFuture(response);
        } catch (CallNotPermittedException e) {
            log.warn("[BATCH-EMOTION] Circuit OPEN for emotionAiService — elapsed={}ms",
                System.currentTimeMillis() - startMs);
            return CompletableFuture.completedFuture(null);
        } catch (HttpServerErrorException e) {
            if (e.getStatusCode().value() == 503) {
                log.warn("[BATCH-EMOTION][OVERLOAD-503] Python emotion service at capacity — elapsed={}ms",
                    System.currentTimeMillis() - startMs);
            } else {
                log.error("[BATCH-EMOTION][HTTP-{}] elapsed={}ms error={}",
                    e.getStatusCode().value(), System.currentTimeMillis() - startMs, e.getMessage());
            }
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("[BATCH-EMOTION][ERROR] elapsed={}ms error={}",
                System.currentTimeMillis() - startMs, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }
}