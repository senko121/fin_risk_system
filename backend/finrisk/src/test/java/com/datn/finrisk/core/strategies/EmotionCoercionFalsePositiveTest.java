package com.datn.finrisk.core.strategies;

import com.datn.finrisk.application.dtos.EmotionAIResponse;
import com.datn.finrisk.application.dtos.FaceAIResponse;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.AiAuditService;
import com.datn.finrisk.core.services.RiskEvaluationService;
import com.datn.finrisk.core.services.biometric.EmotionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P3.2 — Phân tích tín hiệu cưỡng bức và tỉ lệ false-positive cảm xúc.
 *
 * Trả lời câu hỏi phản biện B4.3 và B4.4:
 *   "Ngưỡng FEAR ≥ 0.65 được xác định qua thực nghiệm nào?"
 *   "Điều gì ngăn giao dịch hợp lệ bị chặn khi người dùng cau mày/thiếu ánh sáng?"
 *   "Tỉ lệ false-positive UNDER_REVIEW là bao nhiêu?"
 *
 * Logic kiểm tra (AdvancedFaceActionStrategy.validateFaceAndEmotion):
 *   - FEAR: chỉ là coercion signal khi confidence ≥ 0.65. Dưới ngưỡng → downgrade → UNKNOWN
 *   - STRESS, ANGRY: luôn là coercion signal bất kể confidence
 *   - Mọi emotion khác (HAPPY, NEUTRAL, SURPRISE, DISGUST, ...): an toàn, không UNDER_REVIEW
 *
 * Phương pháp đo FPR:
 *   - "Người dùng bình thường" = tập cảm xúc không phải coercion signal
 *   - Đếm số trường hợp bị UNDER_REVIEW sai (false positive)
 *   - FPR = false_positives / total_normal_cases
 *   - Ngưỡng chấp nhận: FPR ≤ 0.10 (10%)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("P3.2 — Emotion Coercion: FEAR threshold + False-Positive Analysis")
class EmotionCoercionFalsePositiveTest {

    // Ngưỡng FEAR (P1.2 trong codebase)
    private static final double FEAR_THRESHOLD = 0.65;

    // Ngưỡng chấp nhận FPR
    private static final double MAX_ALLOWED_FPR = 0.10;

    @Mock private TransactionRepository transactionRepository;
    @Mock private RiskEvaluationService riskEvaluationService;
    @Mock private AiAuditService aiAuditService;
    @Mock private EmotionEvaluator emotionEvaluator;
    @Mock private ThreadPoolTaskExecutor biometricVerifyExecutor;

    @InjectMocks private AdvancedFaceActionStrategy strategy;

    private List<String> dummyFrames;

    @BeforeEach
    void setUp() {
        dummyFrames = List.of("frame_base64_1", "frame_base64_2", "frame_base64_3");

        // Mock ThreadPoolTaskExecutor để isBiometricExecutorOverloaded() trả false
        ThreadPoolExecutor tpe = mock(ThreadPoolExecutor.class);
        when(tpe.getQueue()).thenReturn(new LinkedBlockingQueue<>());  // queue rỗng
        when(tpe.getActiveCount()).thenReturn(0);
        when(tpe.getMaximumPoolSize()).thenReturn(10);
        when(biometricVerifyExecutor.getThreadPoolExecutor()).thenReturn(tpe);
        when(biometricVerifyExecutor.getQueueCapacity()).thenReturn(50);

        // Void methods: không cần mock
        // transactionRepository.updateEmotionSignal → void, Mockito do-nothing by default
        // aiAuditService.logEmotionScan → void

        // save() khi UNDER_REVIEW được set
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Transaction buildTxWithFace() {
        User user = new User();
        user.setId(1L);
        user.setFaceEmbeddings("[[1.0,2.0,3.0]]");  // hasFaceEmbeddings() = true

        Account account = new Account();
        account.setUser(user);

        Transaction tx = new Transaction();
        tx.setId(100L);
        tx.setFromAccount(account);
        tx.setAmount(new BigDecimal("1000000"));
        return tx;
    }

    /** Mock face: matched=true (danh tính xác nhận thành công). */
    private void mockFaceMatched() {
        FaceAIResponse face = new FaceAIResponse();
        face.setMatched(true);
        when(riskEvaluationService.verifyIdentityAsync(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(face));
    }

    /** Mock face: matched=false (nhận diện thất bại). */
    private void mockFaceNotMatched() {
        FaceAIResponse face = new FaceAIResponse();
        face.setMatched(false);
        when(riskEvaluationService.verifyIdentityAsync(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(face));
    }

    /** Mock cảm xúc với emotion label và confidence. */
    private void mockEmotion(String emotion, double confidence) {
        EmotionAIResponse resp = new EmotionAIResponse();
        resp.setEmotion(emotion);
        resp.setConfidence(confidence);
        resp.setProcessTimeMs(80L);
        resp.setProbDetails(Map.of(emotion.toLowerCase(), confidence));
        when(emotionEvaluator.evaluateSequenceAsync(any()))
                .thenReturn(CompletableFuture.completedFuture(resp));
    }

    // =========================================================================
    // NHÓM 1: NGƯỠNG FEAR 0.65 — kiểm tra boundary condition
    // =========================================================================

    @Nested
    @DisplayName("Nhóm 1 — Ngưỡng FEAR 0.65: boundary value analysis")
    class FearThresholdBoundary {

        /**
         * FEAR với confidence chính xác = 0.65 (tại ngưỡng) → UNDER_REVIEW.
         */
        @Test
        @DisplayName("FEAR confidence = 0.65 (tại ngưỡng) → UNDER_REVIEW")
        void fear_exactThreshold_triggersUnderReview() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("FEAR", FEAR_THRESHOLD);  // = 0.65

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).as("FEAR 0.65 phải trả false (fail validation)").isFalse();
            assertThat(tx.getStatus()).as("FEAR 0.65 phải set UNDER_REVIEW")
                                      .isEqualTo("UNDER_REVIEW");
        }

        /**
         * FEAR với confidence ngay dưới ngưỡng (0.64) → downgrade → UNKNOWN → cho qua.
         */
        @Test
        @DisplayName("FEAR confidence = 0.64 (dưới ngưỡng) → downgrade UNKNOWN → KHÔNG UNDER_REVIEW")
        void fear_justBelowThreshold_doesNotTriggerUnderReview() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("FEAR", 0.64);  // < 0.65

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).as("FEAR 0.64 phải trả true (validation thành công)").isTrue();
            assertThat(tx.getStatus()).as("FEAR 0.64 không được set UNDER_REVIEW")
                                      .isNotEqualTo("UNDER_REVIEW");
        }

        /**
         * FEAR confidence = 0.66 (trên ngưỡng 1 bước) → UNDER_REVIEW.
         */
        @Test
        @DisplayName("FEAR confidence = 0.66 (trên ngưỡng) → UNDER_REVIEW")
        void fear_justAboveThreshold_triggersUnderReview() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("FEAR", 0.66);

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).isFalse();
            assertThat(tx.getStatus()).isEqualTo("UNDER_REVIEW");
        }

        /**
         * FEAR confidence rất thấp (0.30) — ánh sáng yếu, cau mày nhẹ — phải bị lọc.
         * Đây là trường hợp quan trọng: bảo vệ người dùng bình thường khỏi bị chặn sai.
         */
        @Test
        @DisplayName("FEAR confidence = 0.30 (ánh sáng yếu / cau mày nhẹ) → KHÔNG UNDER_REVIEW")
        void fear_lowConfidence_poorLightingOrMildFrown_notBlocked() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("FEAR", 0.30);

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).as("FEAR thấp không được chặn người dùng bình thường").isTrue();
            assertThat(tx.getStatus()).isNotEqualTo("UNDER_REVIEW");
        }

        /**
         * Tham số hóa: test nhiều mức confidence quanh ngưỡng 0.65.
         */
        @ParameterizedTest(name = "FEAR confidence={0} → expectedUnderReview={1}")
        @CsvSource({
            "0.10, false",
            "0.30, false",
            "0.50, false",
            "0.60, false",
            "0.64, false",
            "0.65, true",
            "0.70, true",
            "0.85, true",
            "1.00, true"
        })
        @DisplayName("FEAR — coverage đầy đủ quanh ngưỡng 0.65")
        void fear_parametrized_thresholdBehavior(double confidence, boolean expectedUnderReview) {
            Transaction tx = buildTxWithFace();
            tx.setId((long)(confidence * 1000));  // unique ID per test
            mockFaceMatched();
            mockEmotion("FEAR", confidence);

            strategy.validateFaceAndEmotion(tx, new ArrayList<>(dummyFrames));

            boolean actualUnderReview = "UNDER_REVIEW".equals(tx.getStatus());
            assertThat(actualUnderReview)
                    .as("FEAR conf=%.2f → UNDER_REVIEW=%s".formatted(confidence, expectedUnderReview))
                    .isEqualTo(expectedUnderReview);
        }
    }

    // =========================================================================
    // NHÓM 2: COERCION SIGNALS — STRESS và ANGRY
    // =========================================================================

    @Nested
    @DisplayName("Nhóm 2 — STRESS và ANGRY: luôn là coercion signal (không có threshold)")
    class StressAngrySignals {

        @Test
        @DisplayName("STRESS (conf thấp 0.40) → UNDER_REVIEW — không có threshold lọc")
        void stress_lowConfidence_stillTriggersUnderReview() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("STRESS", 0.40);

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).isFalse();
            assertThat(tx.getStatus()).isEqualTo("UNDER_REVIEW");
        }

        @Test
        @DisplayName("STRESS (conf cao 0.90) → UNDER_REVIEW")
        void stress_highConfidence_triggersUnderReview() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("STRESS", 0.90);

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).isFalse();
            assertThat(tx.getStatus()).isEqualTo("UNDER_REVIEW");
        }

        @Test
        @DisplayName("ANGRY (conf thấp 0.55) → UNDER_REVIEW — không có threshold lọc")
        void angry_lowConfidence_stillTriggersUnderReview() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("ANGRY", 0.55);

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).isFalse();
            assertThat(tx.getStatus()).isEqualTo("UNDER_REVIEW");
        }

        @Test
        @DisplayName("ANGRY (conf cao 0.85) → UNDER_REVIEW")
        void angry_highConfidence_triggersUnderReview() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("ANGRY", 0.85);

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).isFalse();
            assertThat(tx.getStatus()).isEqualTo("UNDER_REVIEW");
        }
    }

    // =========================================================================
    // NHÓM 3: SAFE EMOTIONS — người dùng bình thường không bị chặn
    // =========================================================================

    @Nested
    @DisplayName("Nhóm 3 — Safe emotions: HAPPY/NEUTRAL/SURPRISE/v.v. không UNDER_REVIEW")
    class SafeEmotions {

        @Test
        @DisplayName("HAPPY (conf 0.95) → KHÔNG UNDER_REVIEW")
        void happy_highConfidence_notBlocked() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("HAPPY", 0.95);

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).isTrue();
            assertThat(tx.getStatus()).isNotEqualTo("UNDER_REVIEW");
        }

        @Test
        @DisplayName("NEUTRAL (conf 0.88) → KHÔNG UNDER_REVIEW")
        void neutral_highConfidence_notBlocked() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("NEUTRAL", 0.88);

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).isTrue();
            assertThat(tx.getStatus()).isNotEqualTo("UNDER_REVIEW");
        }

        @Test
        @DisplayName("SURPRISE (conf 0.72) → KHÔNG UNDER_REVIEW")
        void surprise_notBlocked() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("SURPRISE", 0.72);

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).isTrue();
            assertThat(tx.getStatus()).isNotEqualTo("UNDER_REVIEW");
        }

        @Test
        @DisplayName("DISGUST (conf 0.60) → KHÔNG UNDER_REVIEW")
        void disgust_notBlocked() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("DISGUST", 0.60);

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).isTrue();
            assertThat(tx.getStatus()).isNotEqualTo("UNDER_REVIEW");
        }

        @Test
        @DisplayName("SAD (conf 0.75) → KHÔNG UNDER_REVIEW — buồn ≠ bị cưỡng bức")
        void sad_notBlocked() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("SAD", 0.75);

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).isTrue();
            assertThat(tx.getStatus()).isNotEqualTo("UNDER_REVIEW");
        }

        @Test
        @DisplayName("CONTEMPT (conf 0.70) → KHÔNG UNDER_REVIEW")
        void contempt_notBlocked() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("CONTEMPT", 0.70);

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).isTrue();
            assertThat(tx.getStatus()).isNotEqualTo("UNDER_REVIEW");
        }

        @Test
        @DisplayName("CALM (conf 0.80) → KHÔNG UNDER_REVIEW")
        void calm_notBlocked() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("CALM", 0.80);

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).isTrue();
            assertThat(tx.getStatus()).isNotEqualTo("UNDER_REVIEW");
        }
    }

    // =========================================================================
    // NHÓM 4: TỔNG HỢP FPR — dataset "người dùng bình thường"
    // =========================================================================

    @Nested
    @DisplayName("Nhóm 4 — Tổng hợp FPR: dataset người dùng bình thường ≤ 10%")
    class FalsePositiveRateAnalysis {

        /**
         * Dataset 13 trường hợp "người dùng bình thường":
         *   - Cảm xúc an toàn (HAPPY, NEUTRAL, SURPRISE, SAD, DISGUST, CONTEMPT, CALM)
         *   - FEAR với confidence < 0.65 (cau mày nhẹ, ánh sáng yếu, v.v.)
         *
         * Không trường hợp nào trong dataset này nên bị UNDER_REVIEW.
         * FPR = số trường hợp bị chặn sai / tổng dataset.
         */
        @Test
        @DisplayName("📊 FPR ≤ 10%: 13 trường hợp người dùng bình thường không bị UNDER_REVIEW")
        void falsePositiveRate_normalUserDataset_withinThreshold() {

            record EmotionCase(String label, String emotion, double confidence,
                               String description) {}

            List<EmotionCase> normalUserCases = List.of(
                new EmotionCase("N01", "HAPPY",    0.95, "Người dùng vui vẻ, ánh sáng tốt"),
                new EmotionCase("N02", "HAPPY",    0.78, "Người dùng vui vẻ, ánh sáng trung bình"),
                new EmotionCase("N03", "NEUTRAL",  0.88, "Khuôn mặt trung tính — điển hình khi tập trung"),
                new EmotionCase("N04", "NEUTRAL",  0.60, "Trung tính, confidence thấp hơn"),
                new EmotionCase("N05", "SURPRISE", 0.72, "Ngạc nhiên khi thấy số tiền"),
                new EmotionCase("N06", "SAD",      0.75, "Buồn — ví dụ: chuyển tiền viện phí"),
                new EmotionCase("N07", "DISGUST",  0.60, "Khó chịu — không phải coercion"),
                new EmotionCase("N08", "CONTEMPT", 0.70, "Khinh thường — không phải coercion"),
                new EmotionCase("N09", "CALM",     0.80, "Bình tĩnh"),
                // FEAR dưới ngưỡng — các trường hợp nhìn sai bởi FER model
                new EmotionCase("N10", "FEAR",     0.10, "FER2013 nhận sai: FEAR nhưng conf rất thấp"),
                new EmotionCase("N11", "FEAR",     0.30, "Ánh sáng yếu → cau mày → FEAR giả"),
                new EmotionCase("N12", "FEAR",     0.50, "Tập trung → vẻ mặt căng → FEAR giả"),
                new EmotionCase("N13", "FEAR",     0.64, "Ngay dưới ngưỡng 0.65 — vẫn an toàn")
            );

            int falsePositives = 0;
            List<String> blockedCases = new ArrayList<>();

            for (EmotionCase ec : normalUserCases) {
                Transaction tx = buildTxWithFace();
                tx.setId((long) normalUserCases.indexOf(ec) + 200);
                mockFaceMatched();
                mockEmotion(ec.emotion(), ec.confidence());

                strategy.validateFaceAndEmotion(tx, new ArrayList<>(dummyFrames));

                boolean wasUnderReview = "UNDER_REVIEW".equals(tx.getStatus());
                if (wasUnderReview) {
                    falsePositives++;
                    blockedCases.add(ec.label() + " (" + ec.emotion() + " conf=" + ec.confidence() + ")");
                }

                System.out.printf("  [%s] %-10s conf=%.2f → %s %s | %s%n",
                        ec.label(), ec.emotion(), ec.confidence(),
                        wasUnderReview ? "UNDER_REVIEW" : "PASSED     ",
                        wasUnderReview ? "❌ FP" : "✅",
                        ec.description());
            }

            int totalNormal = normalUserCases.size();
            double fpr = (double) falsePositives / totalNormal;

            System.out.printf("""

                ╔══════════════════════════════════════════════════╗
                ║   Emotion Coercion — False Positive Analysis     ║
                ╠══════════════════════════════════════════════════╣
                ║  Dataset "Người dùng bình thường": %2d cases      ║
                ║  False Positives (UNDER_REVIEW sai): %2d          ║
                ║  FPR = %.2f  (ngưỡng chấp nhận ≤ %.2f)          ║
                ╚══════════════════════════════════════════════════╝
                %n""",
                totalNormal, falsePositives, fpr, MAX_ALLOWED_FPR);

            if (!blockedCases.isEmpty()) {
                System.out.println("  ⚠️  Các trường hợp bị chặn sai: " + blockedCases);
            }

            assertThat(fpr).as("FPR phải ≤ %.2f (ngưỡng chấp nhận)".formatted(MAX_ALLOWED_FPR))
                           .isLessThanOrEqualTo(MAX_ALLOWED_FPR);
        }

        /**
         * Dataset coercion: tất cả 7 trường hợp phải bị phát hiện.
         * Đảm bảo recall của tín hiệu cưỡng bức = 100%.
         */
        @Test
        @DisplayName("📊 Recall = 100%: 7 coercion signal thực sự đều bị phát hiện")
        void coercionDetectionRate_allRealCoercions_detected() {

            record CoercionCase(String label, String emotion, double confidence) {}

            List<CoercionCase> coercions = List.of(
                new CoercionCase("C01", "FEAR",   0.65),
                new CoercionCase("C02", "FEAR",   0.80),
                new CoercionCase("C03", "FEAR",   1.00),
                new CoercionCase("C04", "STRESS", 0.40),
                new CoercionCase("C05", "STRESS", 0.90),
                new CoercionCase("C06", "ANGRY",  0.55),
                new CoercionCase("C07", "ANGRY",  0.85)
            );

            int detected = 0;
            List<String> missedCases = new ArrayList<>();

            for (CoercionCase cc : coercions) {
                Transaction tx = buildTxWithFace();
                tx.setId((long) coercions.indexOf(cc) + 300);
                mockFaceMatched();
                mockEmotion(cc.emotion(), cc.confidence());

                strategy.validateFaceAndEmotion(tx, new ArrayList<>(dummyFrames));

                boolean wasDetected = "UNDER_REVIEW".equals(tx.getStatus());
                if (wasDetected) {
                    detected++;
                } else {
                    missedCases.add(cc.label() + " (" + cc.emotion() + " conf=" + cc.confidence() + ")");
                }

                System.out.printf("  [%s] %-6s conf=%.2f → %s %s%n",
                        cc.label(), cc.emotion(), cc.confidence(),
                        wasDetected ? "UNDER_REVIEW" : "PASSED     ",
                        wasDetected ? "✅ TP" : "❌ FN");
            }

            int total = coercions.size();
            double recallCoercion = (double) detected / total;

            System.out.printf("""

                ╔══════════════════════════════════════════════════╗
                ║   Coercion Detection Rate                        ║
                ╠══════════════════════════════════════════════════╣
                ║  Tổng coercion cases: %2d                         ║
                ║  Detected (UNDER_REVIEW): %2d                     ║
                ║  Recall = %.2f (phải = 1.00)                     ║
                ╚══════════════════════════════════════════════════╝
                %n""",
                total, detected, recallCoercion);

            if (!missedCases.isEmpty()) {
                System.out.println("  ⚠️  Bỏ sót: " + missedCases);
            }

            assertThat(recallCoercion).as("Recall coercion phải = 1.00 (bắt hết)")
                                      .isEqualTo(1.0);
        }
    }

    // =========================================================================
    // NHÓM 5: EDGE CASES — face không match + emotion null
    // =========================================================================

    @Nested
    @DisplayName("Nhóm 5 — Edge cases: face không match, emotion null, frames rỗng")
    class EdgeCases {

        @Test
        @DisplayName("Face không match dù emotion bình thường → trả false (nhận diện thất bại)")
        void faceNotMatched_normalEmotion_returnsFalse() {
            Transaction tx = buildTxWithFace();
            mockFaceNotMatched();
            mockEmotion("HAPPY", 0.90);

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(result).isFalse();
            // Không set UNDER_REVIEW vì fail face chứ không phải coercion
            assertThat(tx.getStatus()).isNotEqualTo("UNDER_REVIEW");
        }

        @Test
        @DisplayName("Emotion API trả null → không crash, không UNDER_REVIEW")
        void emotionApiReturnsNull_noUnderReview_noException() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            when(emotionEvaluator.evaluateSequenceAsync(any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            boolean result = strategy.validateFaceAndEmotion(tx, dummyFrames);

            // null emotion → rawEmotion = "UNKNOWN" → không phải coercion
            assertThat(tx.getStatus()).isNotEqualTo("UNDER_REVIEW");
        }

        @Test
        @DisplayName("Frames rỗng → trả false ngay, không gọi AI")
        void emptyFrames_returnsFalseImmediately() {
            Transaction tx = buildTxWithFace();

            boolean result = strategy.validateFaceAndEmotion(tx, List.of());

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Frames null → trả false ngay")
        void nullFrames_returnsFalseImmediately() {
            Transaction tx = buildTxWithFace();

            boolean result = strategy.validateFaceAndEmotion(tx, null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("FEAR uppercase/lowercase khớp đúng — 'fear' bị uppercase trước khi so sánh")
        void fear_lowercaseInput_normalizedToUppercase() {
            Transaction tx = buildTxWithFace();
            mockFaceMatched();
            mockEmotion("fear", 0.90);  // API trả lowercase

            // rawEmotion = "fear".toUpperCase() = "FEAR" → áp dụng threshold → UNDER_REVIEW
            strategy.validateFaceAndEmotion(tx, dummyFrames);

            assertThat(tx.getStatus()).as("'fear' lowercase phải được normalize và xử lý đúng")
                                      .isEqualTo("UNDER_REVIEW");
        }
    }
}
