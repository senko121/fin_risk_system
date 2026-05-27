package com.datn.finrisk.core.strategies;

import com.datn.finrisk.application.dtos.FaceAIResponse;
import com.datn.finrisk.application.dtos.EmotionAIResponse;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.exceptions.BusinessLogicException;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.RiskEvaluationService;
import com.datn.finrisk.core.services.AiAuditService;
import com.datn.finrisk.core.services.biometric.EmotionEvaluator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service("advancedFaceActionStrategy") 
public class AdvancedFaceActionStrategy implements RiskActionStrategy {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private RiskEvaluationService riskEvaluationService;
    @Autowired private AiAuditService aiAuditService;
    private static final Logger log =
    LoggerFactory.getLogger(AdvancedFaceActionStrategy.class);

    @Autowired
    @Qualifier("batchEmotionEvaluator")
    private EmotionEvaluator emotionEvaluator;

    @Autowired
    @Qualifier("biometricVerifyExecutor")
    private ThreadPoolTaskExecutor biometricVerifyExecutor;

    private static final int AI_PARALLEL_TIMEOUT_SECONDS = 20;
    private static final int BIOMETRIC_QUEUE_WARN_THRESHOLD   = 30;
    private static final int BIOMETRIC_QUEUE_REJECT_THRESHOLD = 40;

    private boolean isBiometricExecutorOverloaded() {
        ThreadPoolExecutor tpe    = biometricVerifyExecutor.getThreadPoolExecutor();
        int queueSize           = tpe.getQueue().size();
        int activeCount         = tpe.getActiveCount();
        int maxPool             = tpe.getMaximumPoolSize();
        int queueCapacity       = biometricVerifyExecutor.getQueueCapacity();

        if (queueSize >= BIOMETRIC_QUEUE_REJECT_THRESHOLD) {
            log.warn("[FaceAI][OVERLOAD] biometricVerifyExecutor saturated: queue={}/{} active={}/{} — fast reject",
                queueSize, queueCapacity, activeCount, maxPool);
            return true;
        }
        if (queueSize >= BIOMETRIC_QUEUE_WARN_THRESHOLD) {
            log.warn("[FaceAI][PRESSURE] biometricVerifyExecutor under pressure: queue={}/{} active={}/{} — accepting",
                queueSize, queueCapacity, activeCount, maxPool);
        }
        return false;
    }

    @Override
    public Transaction execute(Transaction tx) {
        log.info("[FaceStrategy][EXECUTE] tx={} riskLevel=HIGH → PENDING_PIN_HIGH", tx.getId());
        
        User user = tx.getFromAccount().getUser();
        if (!user.hasFaceEmbeddings()) {
            throw new BusinessLogicException("ERR_NO_FACE_SETUP",
                "Giao dịch rủi ro cao. Bạn chưa cài đặt FaceID, vui lòng thiết lập trước khi thực hiện!");
        }

        tx.setRiskLevel("HIGH");
        tx.setStatus("PENDING_PIN_HIGH"); 
        return transactionRepository.save(tx);
    }
    
    public boolean validateFaceAndEmotion(Transaction tx, List<String> liveImageFrames) {
        if (isBiometricExecutorOverloaded()) {
            log.error("[FaceAI][OVERLOAD] tx={} biometricVerifyExecutor at capacity — fast reject (REST path)", tx.getId());
            return false;
        }

        if (liveImageFrames == null || liveImageFrames.isEmpty()) {
            log.error("[FaceAI][SYNC] No frames provided for tx={}", tx.getId());
            return false;
        }

        User user = tx.getFromAccount().getUser();
        if (!user.hasFaceEmbeddings()) {
            log.error("[FaceAI][SYNC] No face data for tx={}", tx.getId());
            return false;
        }

        log.info("[FaceAI][SYNC-START] tx={} frames={} submitting parallel AI tasks", tx.getId(), liveImageFrames.size());

        CompletableFuture<FaceAIResponse> identityTask =
            riskEvaluationService.verifyIdentityAsync(liveImageFrames, user); // Truyền user object
            
        CompletableFuture<EmotionAIResponse> emotionTask = 
            emotionEvaluator.evaluateSequenceAsync(liveImageFrames);

        try {
            CompletableFuture.allOf(identityTask, emotionTask).get(AI_PARALLEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            FaceAIResponse idResult = identityTask.get();
            EmotionAIResponse emotionResult = emotionTask.get();

            String emotion = (emotionResult != null && emotionResult.getEmotion() != null)
                             ? emotionResult.getEmotion().toUpperCase() : "UNKNOWN";

            log.info("[FaceAI][SYNC-RESULT] tx={} matched={} distance={} band={} emotion={}",
                tx.getId(),
                idResult != null && idResult.isMatched(),
                idResult != null ? String.format("%.4f", idResult.getSimilarityDistance()) : "N/A",
                idResult != null ? idResult.getConfidenceBand() : "N/A",
                emotion);

            if (idResult != null) {
                log.info("[FaceAI][LIVENESS] tx={} liveness_pass={} liveness_score={} spoof_detected={}",
                    tx.getId(),
                    idResult.getLivenessPass(),
                    idResult.getLivenessScore() != null ? String.format("%.4f", idResult.getLivenessScore()) : "N/A",
                    Boolean.TRUE.equals(idResult.getSpoofDetected()));
                if (Boolean.TRUE.equals(idResult.getSpoofDetected())) {
                    log.warn("[FaceAI][SPOOF-DETECTED] tx={} — presentation attack in identity result", tx.getId());
                }
            }

            transactionRepository.updateEmotionSignal(tx.getId(), emotion);
            tx.setEmotionSignal(emotion);

            if (emotionResult != null) {
                aiAuditService.logEmotionScan(tx, emotionResult);
            }

            if (idResult == null || !idResult.isMatched()) {
                return false;
            }

            if ("FEAR".equals(emotion) || "STRESS".equals(emotion) || "ANGRY".equals(emotion)) {
                log.warn("[FaceAI][SYNC-COERCION] tx={} emotion={} — setting UNDER_REVIEW", tx.getId(), emotion);
                tx.setStatus("UNDER_REVIEW");
                String currentDesc = tx.getDescription() != null ? tx.getDescription() : "";
                tx.setDescription("[CẢNH BÁO BẢO MẬT: AI PHÁT HIỆN " + emotion + "] " + currentDesc);
                transactionRepository.save(tx);
                return false;
            }

            return true;

        } catch (TimeoutException e) {
            identityTask.cancel(true);
            emotionTask.cancel(true);
            log.error("[FaceAI][SYNC-TIMEOUT] tx={} exceeded {}s — tasks cancelled", tx.getId(), AI_PARALLEL_TIMEOUT_SECONDS);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[FaceAI][SYNC-INTERRUPTED] tx={}", tx.getId());
            return false;
        } catch (Exception e) {
            log.error("[FaceAI][SYNC-ERROR] tx={} error={}", tx.getId(), e.getMessage());
            return false;
        }
    }

    public CompletableFuture<Boolean> validateFaceAndEmotionAsync(Transaction tx, List<String> liveImageFrames) {
        long startMs = System.currentTimeMillis();

        if (isBiometricExecutorOverloaded()) {
            log.error("[FaceAI][ASYNC-OVERLOAD] tx={} biometricVerifyExecutor at capacity — fast reject (WS path)", tx.getId());
            return CompletableFuture.completedFuture(false);
        }

        if (liveImageFrames == null || liveImageFrames.isEmpty()) {
            log.error("[FaceAI][ASYNC] No frames for tx={}", tx.getId());
            return CompletableFuture.completedFuture(false);
        }

        User userAsync = tx.getFromAccount().getUser();
        if (!userAsync.hasFaceEmbeddings()) {
            log.error("[FaceAI][ASYNC] No face data for tx={}", tx.getId());
            return CompletableFuture.completedFuture(false);
        }

        log.info("[FaceAI][ASYNC-START] tx={} frames={}", tx.getId(), liveImageFrames.size());

        CompletableFuture<FaceAIResponse> identityTask =
            riskEvaluationService.verifyIdentityAsync(liveImageFrames, userAsync); // Truyền user object
        CompletableFuture<EmotionAIResponse> emotionTask =
            emotionEvaluator.evaluateSequenceAsync(liveImageFrames);

        return CompletableFuture.allOf(identityTask, emotionTask)
            .orTimeout(AI_PARALLEL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .thenApply(v -> {
                long elapsed = System.currentTimeMillis() - startMs;
                FaceAIResponse    idResult      = identityTask.getNow(null);
                EmotionAIResponse emotionResult = emotionTask.getNow(null);

                String emotion = (emotionResult != null && emotionResult.getEmotion() != null)
                                 ? emotionResult.getEmotion().toUpperCase() : "UNKNOWN";

                log.info("[FaceAI][ASYNC-RESULT] tx={} elapsed={}ms matched={} distance={} band={} emotion={}",
                    tx.getId(), elapsed,
                    idResult != null && idResult.isMatched(),
                    idResult != null ? String.format("%.4f", idResult.getSimilarityDistance()) : "N/A",
                    idResult != null ? idResult.getConfidenceBand() : "N/A",
                    emotion);

                if (idResult != null) {
                    log.info("[FaceAI][LIVENESS] tx={} liveness_pass={} liveness_score={} spoof_detected={}",
                        tx.getId(),
                        idResult.getLivenessPass(),
                        idResult.getLivenessScore() != null ? String.format("%.4f", idResult.getLivenessScore()) : "N/A",
                        Boolean.TRUE.equals(idResult.getSpoofDetected()));
                }

                transactionRepository.updateEmotionSignal(tx.getId(), emotion);
                tx.setEmotionSignal(emotion);

                if (emotionResult != null) {
                    aiAuditService.logEmotionScan(tx, emotionResult);
                }

                if (idResult == null || !idResult.isMatched()) {
                    log.warn("[FaceAI][ASYNC-REJECT] tx={} elapsed={}ms — identity not matched", tx.getId(), elapsed);
                    return false;
                }

                if ("FEAR".equals(emotion) || "STRESS".equals(emotion) || "ANGRY".equals(emotion)) {
                    log.warn("[FaceAI][ASYNC-COERCION] tx={} elapsed={}ms emotion={} — setting UNDER_REVIEW", tx.getId(), elapsed, emotion);
                    tx.setStatus("UNDER_REVIEW");
                    String desc = tx.getDescription() != null ? tx.getDescription() : "";
                    tx.setDescription("[CẢNH BÁO BẢO MẬT: AI PHÁT HIỆN " + emotion + "] " + desc);
                    transactionRepository.save(tx);
                    return false;
                }

                log.info("[FaceAI][ASYNC-PASS] tx={} elapsed={}ms — identity + emotion OK", tx.getId(), elapsed);
                return true;
            })
            .exceptionally(ex -> {
                long elapsed = System.currentTimeMillis() - startMs;
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof TimeoutException) {
                    identityTask.cancel(true);
                    emotionTask.cancel(true);
                    log.error("[FaceAI][ASYNC-TIMEOUT] tx={} exceeded {}s at {}ms — tasks cancelled", tx.getId(), AI_PARALLEL_TIMEOUT_SECONDS, elapsed);
                } else if (cause instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    log.warn("[FaceAI][ASYNC-INTERRUPTED] tx={} at {}ms", tx.getId(), elapsed);
                } else {
                    log.error("[FaceAI][ASYNC-ERROR] tx={} at {}ms: {}", tx.getId(), elapsed, cause.getMessage());
                }
                return false;
            });
    }
}