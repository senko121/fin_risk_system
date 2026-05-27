package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Rule;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.TransactionAiInsight;
import com.datn.finrisk.core.repository.RuleRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.datn.finrisk.application.dtos.EmotionAIResponse;
import com.datn.finrisk.application.dtos.FaceAIResponse;
import com.datn.finrisk.application.dtos.BehaviorInsightResult;
import com.datn.finrisk.application.dtos.TransactionSpelContext;
import com.datn.finrisk.core.repository.RiskScoreRepository;
import com.datn.finrisk.core.repository.UserDeviceRepository;
import com.datn.finrisk.core.entities.RiskScore;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.web.client.HttpServerErrorException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
public class RiskEvaluationService {

    @Value("${ai.service.face-url:http://localhost:5000/api/ai/verify-face}")
    private String faceAiUrl;

    @Value("${ai.service.emotion-url:http://localhost:5001/api/ai/detect-emotion}")
    private String emotionAiUrl;

    @Value("${ai.service.emotion-sequence-url:http://localhost:5001/api/ai/detect-emotion-sequence}")
    private String emotionSequenceAiUrl;

    @Value("${ai.service.voice-url:http://localhost:5003/api/ai/verify-voice}")
    private String voiceAiUrl;

    @Autowired private RestTemplate restTemplate;
    @Autowired private RuleRepository ruleRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private RiskScoreRepository riskScoreRepository;
    @Autowired private com.datn.finrisk.core.repository.UserBehaviorProfileRepository profileRepository;
    @Autowired private com.datn.finrisk.core.services.BehavioralProfilingService behavioralProfilingService;
    @Autowired private UserDeviceRepository userDeviceRepository;
    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired private FaceEnrollService faceEnrollService;  // ← THÊM MỚI

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExpressionParser parser = new SpelExpressionParser();

    private static final int AI_MATURE_COUNT = 150;
    private static final int AI_ACTIVE_COUNT = 50;
    private static final double AI_SCORE_FACTOR = 0.35;

    // Category caps: correlated signals trong cùng domain không thể inflate lẫn nhau
    private static final Map<String, Integer> CATEGORY_CAPS = Map.of(
        "DEVICE",      30,
        "FINANCIAL",   40,
        "BIOMETRIC",   55,
        "VELOCITY",    30,
        "CONTEXTUAL",  20,
        "COMPOSITE",   40
    );

    private static final Map<String, Integer> OVERRIDE_PRIORITY = Map.of(
        "MEDIUM_1", 1,
        "MEDIUM_2", 2,
        "HIGH",     3
    );

    // =========================================================================
    // evaluateRisk — interaction-aware category scoring (v2)
    // =========================================================================
    public int evaluateRisk(Transaction transaction, boolean isNewRecipient,
                            List<RiskScore> pendingRiskLogs,
                            List<TransactionAiInsight> pendingAiInsights) {
        log.debug("[RISK-ENGINE][START] tx={} evaluating risk", transaction.getId());

        User sender = transaction.getFromAccount().getUser();
        List<Rule> activeRules = ruleRepository.findByIsActiveTrue();

        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        int recentTxCount = transactionRepository.countRecentTransactions(
                transaction.getFromAccount().getId(), oneMinuteAgo);

        double balanceRatio = 0.0;
        double currentBalance = transaction.getFromAccount().getBalance().doubleValue();
        if (currentBalance > 0) {
            balanceRatio = transaction.getAmount().doubleValue() / currentBalance;
        }

        int currentHour = LocalDateTime.now().getHour();
        boolean isNightTime = (currentHour >= 23 || currentHour < 5);

        LocalDateTime startOfDay = java.time.LocalDate.now().atStartOfDay();
        BigDecimal sumToday = transactionRepository.sumSuccessfulAmountToday(
                transaction.getFromAccount().getId(), startOfDay);
        double totalTransferredToday = (sumToday != null) ? sumToday.doubleValue() : 0.0;
        // Chỉ tính lịch sử đã chuyển — giao dịch đang xét chưa committed
        double dailyTotalAmount = totalTransferredToday;

        com.datn.finrisk.core.entities.UserBehaviorProfile profile =
                profileRepository.findByUserId(sender.getId()).orElse(null);

        double gapSeconds = 86400.0;
        if (profile != null && profile.getLastTxTimestamp() != null) {
            gapSeconds = java.time.Duration.between(
                    profile.getLastTxTimestamp(), LocalDateTime.now()).getSeconds();
        }

        double recipientNovelty = isNewRecipient ? 1.0 : 0.0;
        BehaviorInsightResult behaviorResult = behavioralProfilingService
                .calculateBehavioralAnomalyScore(transaction, profile, gapSeconds, recipientNovelty);

        int behavioralScore = behaviorResult.getTotalScore();
        pendingAiInsights.addAll(behaviorResult.getInsights());

        int txCount = (profile != null) ? profile.getTxCount() : 0;

        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        context.setVariable("tx", TransactionSpelContext.from(transaction));
        context.setVariable("isNewRecipient", isNewRecipient);
        boolean combinedSuspiciousRisk = sender.isSuspiciousSession() || sender.isAdminFlagged();
        context.setVariable("suspiciousSession", combinedSuspiciousRisk);
        boolean deviceTrusted = resolveDeviceTrusted(transaction, sender);
        context.setVariable("deviceTrusted", deviceTrusted);
        context.setVariable("recentTxCount", recentTxCount);
        context.setVariable("balanceRatio", balanceRatio);
        context.setVariable("isNightTime", isNightTime);
        context.setVariable("dailyTotalAmount", dailyTotalAmount);

        // ── Phase 1: VETO sweep ────────────────────────────────────────────────
        // VETO rules bypass scoring hoàn toàn — emergency signal (e.g. FEAR/coercion)
        for (Rule rule : activeRules) {
            if (!"VETO".equals(rule.getRuleType())) continue;
            try {
                String spel = rule.getSpelExpression();
                if (spel == null || spel.isEmpty()) continue;
                Boolean matched = parser.parseExpression(spel).getValue(context, Boolean.class);
                if (Boolean.TRUE.equals(matched)) {
                    log.warn("[RISK-ENGINE][VETO] tx={} rule='{}' — returning 100 immediately",
                            transaction.getId(), rule.getRuleName());
                    RiskScore riskLog = new RiskScore();
                    riskLog.setRule(rule);
                    riskLog.setAppliedScore(rule.getActionScore());
                    pendingRiskLogs.add(riskLog);
                    transaction.setPolicyOverride("HIGH");
                    return 100;
                }
            } catch (SpelParseException | EvaluationException e) {
                log.warn("SpEL error VETO rule [id={}, name='{}'] — skipped: {}",
                        rule.getId(), rule.getRuleName(), e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error VETO rule [id={}, name='{}'] — skipped.",
                        rule.getId(), rule.getRuleName(), e);
            }
        }

        // ── Phase 2: Category bucket scoring ──────────────────────────────────
        // Rules gom vào bucket theo category, mỗi bucket có cap riêng.
        // Ngăn correlated signals (device + session) inflate lẫn nhau.
        Map<String, Integer> categoryTotals = new HashMap<>();
        String activeOverride = null;

        for (Rule rule : activeRules) {
            if ("VETO".equals(rule.getRuleType())) continue;
            try {
                String spel = rule.getSpelExpression();
                if (spel == null || spel.isEmpty()) continue;
                Boolean matched = parser.parseExpression(spel).getValue(context, Boolean.class);
                if (Boolean.TRUE.equals(matched)) {
                    int score = rule.getActionScore();
                    String cat = (rule.getCategory() != null && !rule.getCategory().isBlank())
                            ? rule.getCategory() : "CONTEXTUAL";
                    categoryTotals.merge(cat, score, Integer::sum);

                    String override = rule.getMinPolicyOverride();
                    if (override != null && !override.isBlank()) {
                        if (activeOverride == null ||
                            OVERRIDE_PRIORITY.getOrDefault(override, 0) >
                            OVERRIDE_PRIORITY.getOrDefault(activeOverride, 0)) {
                            activeOverride = override;
                        }
                    }
                    if (score != 0) {
                        RiskScore riskLog = new RiskScore();
                        riskLog.setRule(rule);
                        riskLog.setAppliedScore(score);
                        pendingRiskLogs.add(riskLog);
                    }
                }
            } catch (SpelParseException | EvaluationException e) {
                log.warn("SpEL error rule [id={}, name='{}'] — skipped: {}",
                        rule.getId(), rule.getRuleName(), e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error rule [id={}, name='{}'] — skipped.",
                        rule.getId(), rule.getRuleName(), e);
            }
        }

        // Áp dụng category cap — tổng mỗi bucket không vượt giới hạn domain
        int catTotal = 0;
        for (Map.Entry<String, Integer> entry : categoryTotals.entrySet()) {
            int cap     = CATEGORY_CAPS.getOrDefault(entry.getKey(), 20);
            int clamped = Math.max(0, Math.min(entry.getValue(), cap));
            catTotal += clamped;
        }

        // ── Phase 3: AI behavioral contribution ───────────────────────────────
        // AI đóng góp dạng additive (max 35 pts), scale theo độ trưởng thành profile
        double aiReliability;
        if (txCount < AI_ACTIVE_COUNT) {
            aiReliability = 0.0;
        } else if (txCount >= AI_MATURE_COUNT) {
            aiReliability = 1.0;
        } else {
            aiReliability = (double)(txCount - AI_ACTIVE_COUNT) / (AI_MATURE_COUNT - AI_ACTIVE_COUNT);
        }
        int aiContribution = (int) Math.round(behavioralScore * AI_SCORE_FACTOR * aiReliability);

        // ── Phase 4: Final score ───────────────────────────────────────────────
        int finalRiskScore = Math.min(100, catTotal + aiContribution);

        // ── Phase 5: Policy override floors ───────────────────────────────────
        if (activeOverride != null) {
            transaction.setPolicyOverride(activeOverride);
        }

        log.info("[RISK-ENGINE][RESULT] tx={} final={} cat_total={} ai={} cats={} override={}",
                transaction.getId(), finalRiskScore, catTotal, aiContribution,
                categoryTotals, activeOverride != null ? activeOverride : "NONE");
        return finalRiskScore;
    }

    @Async("biometricVerifyExecutor")
    public CompletableFuture<FaceAIResponse> verifyIdentityAsync(
            List<String> liveFrames, User user) {

        if (!user.hasFaceEmbeddings()) {
            log.error("[FACE-ID][NO-DATA] userId={} has no face embeddings", user.getId());
            return CompletableFuture.completedFuture(null);
        }

        List<List<Double>> embeddings = faceEnrollService.parseEmbeddings(user.getFaceEmbeddings());
        if (embeddings == null || embeddings.isEmpty()) {
            log.error("[FACE-ID][PARSE-FAIL] userId={} failed to parse face_embeddings", user.getId());
            return CompletableFuture.completedFuture(null);
        }

        log.info("[FACE-ID][START] userId={} embeddings={} frames={}",
                user.getId(), embeddings.size(), liveFrames != null ? liveFrames.size() : 0);
        return verifyIdentityWithEmbeddingsAsync(liveFrames, embeddings);
    }

    @Async("biometricVerifyExecutor")
    public CompletableFuture<FaceAIResponse> verifyFaceStaticAsync(
            User user, String capturedBase64) {
        return doVerifyFaceStaticMulti(user, List.of(capturedBase64));
    }

    @Async("biometricVerifyExecutor")
    public CompletableFuture<FaceAIResponse> verifyFaceStaticAsync(
            User user, List<String> frames) {
        return doVerifyFaceStaticMulti(user, frames);
    }

    private CompletableFuture<FaceAIResponse> doVerifyFaceStaticMulti(User user, List<String> frames) {
        if (!user.hasFaceEmbeddings()) {
            log.error("[FACE-STATIC][NO-DATA] userId={} has no face embeddings", user.getId());
            return CompletableFuture.completedFuture(null);
        }

        List<List<Double>> embeddings = faceEnrollService.parseEmbeddings(user.getFaceEmbeddings());
        if (embeddings == null || embeddings.isEmpty()) {
            log.error("[FACE-STATIC][PARSE-FAIL] userId={} failed to parse face_embeddings", user.getId());
            return CompletableFuture.completedFuture(null);
        }

        log.info("[FACE-STATIC][START] userId={} embeddings={} frames={}",
                user.getId(), embeddings.size(), frames.size());
        return doVerifyFaceStaticWithEmbeddings(embeddings, frames);
    }

    // =========================================================================
    // PRIVATE helpers
    // =========================================================================

    /** Gọi Python verify với registered_embeddings (multi-angle). */
    private CompletableFuture<FaceAIResponse> verifyIdentityWithEmbeddingsAsync(
            List<String> liveFrames, List<List<Double>> embeddings) {
        long startMs = System.currentTimeMillis();
        log.info("[FACE-ID][START] mode=MULTI_EMBEDDING frames={} registered={}",
                liveFrames != null ? liveFrames.size() : 0, embeddings.size());
        try {
            Map<String, Object> requestMap = buildLiveFrameMap(liveFrames);
            requestMap.put("registered_embeddings", embeddings);
            return doCallFaceApi(requestMap, startMs, "FACE-ID");
        } catch (Exception e) {
            log.error("[FACE-ID][ERROR] elapsed={}ms error={}",
                    System.currentTimeMillis() - startMs, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Gọi Python verify-face static với multi-angle embeddings. */
    private CompletableFuture<FaceAIResponse> doVerifyFaceStaticWithEmbeddings(
            List<List<Double>> embeddings, List<String> frames) {
        long startMs = System.currentTimeMillis();
        log.info("[FACE-STATIC][START] mode=MULTI_EMBEDDING registered={} frames={}",
                embeddings.size(), frames.size());
        try {
            Map<String, Object> body = buildLiveFrameMap(frames);
            body.put("registered_embeddings", embeddings);
            return doCallFaceApi(body, startMs, "FACE-STATIC");
        } catch (Exception e) {
            log.error("[FACE-STATIC][ERROR] elapsed={}ms error={}",
                    System.currentTimeMillis() - startMs, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Build request body với live frames. */
    private Map<String, Object> buildLiveFrameMap(List<String> liveFrames) {
        Map<String, Object> map = new HashMap<>();
        if (liveFrames != null && liveFrames.size() > 1) {
            map.put("live_image_base64_list", liveFrames);
        } else if (liveFrames != null && !liveFrames.isEmpty()) {
            map.put("live_image_base64", liveFrames.get(0));
        }
        return map;
    }

    /** Gọi Python /api/ai/verify-face với circuit breaker và logging. */
    private CompletableFuture<FaceAIResponse> doCallFaceApi(
            Map<String, Object> requestMap, long startMs, String logPrefix) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestMap, headers);

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("faceAiService");
            FaceAIResponse response = cb.executeSupplier(
                    () -> restTemplate.postForObject(faceAiUrl, entity, FaceAIResponse.class));

            long elapsed = System.currentTimeMillis() - startMs;
            if (response != null) {
                log.info("[{}][DONE] elapsed={}ms matched={} distance={} band={} backend={}",
                        logPrefix, elapsed, response.isMatched(),
                        response.getSimilarityDistance(), response.getConfidenceBand(),
                        response.getBackendUsed());
            } else {
                log.warn("[{}][DONE] elapsed={}ms — null response from Python", logPrefix, elapsed);
            }
            if (elapsed > 12_000) {
                log.warn("[{}][SLOW] elapsed={}ms — executor may be under pressure", logPrefix, elapsed);
            }
            return CompletableFuture.completedFuture(response);

        } catch (CallNotPermittedException e) {
            log.warn("[{}] Circuit OPEN — elapsed={}ms", logPrefix, System.currentTimeMillis() - startMs);
            return CompletableFuture.completedFuture(null);
        } catch (HttpServerErrorException e) {
            if (e.getStatusCode().value() == 503) {
                log.warn("[{}][OVERLOAD-503] Python at capacity. elapsed={}ms",
                        logPrefix, System.currentTimeMillis() - startMs);
            } else {
                log.error("[{}][HTTP-{}] elapsed={}ms", logPrefix,
                        e.getStatusCode().value(), System.currentTimeMillis() - startMs);
            }
            return CompletableFuture.completedFuture(null);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            if (body.contains("LIVENESS_FAILED")) {
                log.warn("[{}][LIVENESS-FAIL] elapsed={}ms body={}",
                        logPrefix, System.currentTimeMillis() - startMs, body);
            } else if (body.contains("REPLAY_ATTACK_DETECTED")) {
                log.warn("[{}][REPLAY-DETECTED] elapsed={}ms body={}",
                        logPrefix, System.currentTimeMillis() - startMs, body);
            } else {
                log.warn("[{}][HTTP-400] elapsed={}ms body={}",
                        logPrefix, System.currentTimeMillis() - startMs, body);
            }
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("[{}][ERROR] elapsed={}ms error={}",
                    logPrefix, System.currentTimeMillis() - startMs, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    // =========================================================================
    // detectEmotionAsync + verifyVoiceLivenessBase64Async — không thay đổi
    // =========================================================================
    @Async("realtimeEmotionExecutor")
    public CompletableFuture<EmotionAIResponse> detectEmotionAsync(String liveBase64) {
        long startMs = System.currentTimeMillis();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("image_base64", liveBase64);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestMap, headers);
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("emotionAiService");
            EmotionAIResponse response = cb.executeSupplier(
                    () -> restTemplate.postForObject(emotionAiUrl, entity, EmotionAIResponse.class));
            long elapsed = System.currentTimeMillis() - startMs;
            if (response != null) {
                log.debug("[RT-EMOTION][DONE] elapsed={}ms emotion={}", elapsed, response.getEmotion());
            }
            if (elapsed > 1_000) {
                log.warn("[RT-EMOTION][SLOW] elapsed={}ms", elapsed);
            }
            return CompletableFuture.completedFuture(response);
        } catch (CallNotPermittedException e) {
            log.warn("[RT-EMOTION] Circuit OPEN");
            return CompletableFuture.completedFuture(null);
        } catch (HttpServerErrorException e) {
            log.error("[RT-EMOTION][HTTP-{}] elapsed={}ms",
                    e.getStatusCode().value(), System.currentTimeMillis() - startMs);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("[RT-EMOTION][ERROR] elapsed={}ms error={}",
                    System.currentTimeMillis() - startMs, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<String> verifyVoiceLivenessBase64Async(String audioBase64) {
        long startMs = System.currentTimeMillis();
        log.info("[VOICE-AI][START] thread={}", Thread.currentThread().getName());
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            byte[] decodedAudio = java.util.Base64.getDecoder().decode(audioBase64);
            org.springframework.util.MultiValueMap<String, Object> body =
                    new org.springframework.util.LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(decodedAudio) {
                @Override public String getFilename() { return "websocket_voice.wav"; }
            };
            body.add("audio_file", fileResource);
            HttpEntity<org.springframework.util.MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("voiceAiService");
            JsonNode response = cb.executeSupplier(
                    () -> restTemplate.postForObject(voiceAiUrl, requestEntity, JsonNode.class));
            long elapsed = System.currentTimeMillis() - startMs;
            if (response != null && response.has("authCode")) {
                String authCode = response.get("authCode").asText();
                log.info("[VOICE-AI][DONE] elapsed={}ms authCode_length={}", elapsed, authCode.length());
                return CompletableFuture.completedFuture(authCode);
            }
            log.warn("[VOICE-AI][DONE] elapsed={}ms — no authCode", elapsed);
            return CompletableFuture.completedFuture("");
        } catch (CallNotPermittedException e) {
            log.warn("[VOICE-AI] Circuit OPEN — elapsed={}ms", System.currentTimeMillis() - startMs);
            return CompletableFuture.completedFuture("");
        } catch (Exception e) {
            log.error("[VOICE-AI][ERROR] elapsed={}ms error={}",
                    System.currentTimeMillis() - startMs, e.getMessage());
            return CompletableFuture.completedFuture("");
        }
    }

    private boolean resolveDeviceTrusted(Transaction transaction, User sender) {
        String fingerprint = transaction.getDeviceFingerprint();
        if (fingerprint == null || fingerprint.isBlank()) return false;
        return userDeviceRepository.findByDeviceFingerprint(fingerprint)
                .filter(d -> d.getUser().getId().equals(sender.getId()))
                .map(d -> Boolean.TRUE.equals(d.getIsTrusted()))
                .orElse(false);
    }
}