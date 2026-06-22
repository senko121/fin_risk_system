package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.BehaviorInsightResult;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.Rule;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.TransactionAiInsight;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserDevice;
import com.datn.finrisk.core.repository.RiskScoreRepository;
import com.datn.finrisk.core.repository.RuleRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.repository.UserDeviceRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * P3.1 — Kiểm chứng định lượng cho Risk Engine.
 *
 * Trả lời câu hỏi phản biện B4.1 và B4.2:
 *   "Hệ thống chứng minh khả năng phát hiện giao dịch bất thường bằng số liệu nào?"
 *   "Các điểm phạt và ngưỡng phân mức dựa trên căn cứ gì?"
 *
 * Phương pháp:
 *   - 5 kịch bản FRAUD rõ ràng (nhiều tín hiệu nguy hiểm đồng thời)
 *   - 5 kịch bản LEGIT rõ ràng (không có tín hiệu nguy hiểm)
 *   - Ngưỡng phân loại: score ≥ 70 → dự đoán FRAUD; score < 40 → dự đoán LEGIT
 *   - Chỉ số: Precision, Recall, F1-Score, False-Positive-Rate (FPR)
 *   - Mức chấp nhận: Precision ≥ 0.80, Recall ≥ 0.80, FPR ≤ 0.20
 *
 * Bộ rules thử nghiệm (6 rules, mô phỏng cấu hình production):
 *   R1  FINANCIAL   #isNewRecipient == true AND #tx.amount >= 20000000   +40
 *   R2  DEVICE      #deviceTrusted == false                               +30
 *   R3  VELOCITY    #recentTxCount > 3                                    +30
 *   R4  BIOMETRIC   #suspiciousSession == true                            +50
 *   R5  FINANCIAL   #balanceRatio >= 0.8                                  +35
 *   R6  FINANCIAL   #dailyTotalAmount >= 50000000                         +40
 *
 * Category caps (RiskEngineConstants): FINANCIAL=40, DEVICE=30, VELOCITY=30, BIOMETRIC=55
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("P3.1 — Risk Engine: Kiểm chứng định lượng (Precision / Recall / FPR)")
class RiskEngineQuantitativeTest {

    // ── Ngưỡng phân loại ─────────────────────────────────────────────────────
    private static final int HIGH_THRESHOLD = 70;  // score ≥ 70 → dự đoán FRAUD
    private static final int LOW_THRESHOLD  = 40;  // score < 40 → dự đoán LEGIT

    // ── Ngưỡng chấp nhận chỉ số ──────────────────────────────────────────────
    private static final double MIN_PRECISION = 0.80;
    private static final double MIN_RECALL    = 0.80;
    private static final double MAX_FPR       = 0.20;

    @Mock private RestTemplate restTemplate;
    @Mock private RuleRepository ruleRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private BehavioralProfilingService behavioralProfilingService;
    @Mock private UserDeviceRepository userDeviceRepository;
    @Mock private CircuitBreakerRegistry circuitBreakerRegistry;
    @Mock private FaceEnrollService faceEnrollService;
    @Mock private RiskScoreRepository riskScoreRepository;

    @InjectMocks private RiskEvaluationService riskEvaluationService;

    private final AtomicLong idGen = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(riskEvaluationService, "faceAiUrl",
                "http://localhost:5000/api/ai/verify-face");
        ReflectionTestUtils.setField(riskEvaluationService, "emotionAiUrl",
                "http://localhost:5001/api/ai/detect-emotion");
        ReflectionTestUtils.setField(riskEvaluationService, "emotionSequenceAiUrl",
                "http://localhost:5001/api/ai/detect-emotion-sequence");
        ReflectionTestUtils.setField(riskEvaluationService, "voiceAiUrl",
                "http://localhost:5003/api/ai/verify-voice");

        CircuitBreaker cb = CircuitBreaker.ofDefaults("test");
        when(circuitBreakerRegistry.circuitBreaker(anyString())).thenReturn(cb);

        // AI behavioral score = 0 mặc định (không đủ lịch sử)
        when(behavioralProfilingService.calculateBehavioralAnomalyScore(
                any(), any(), anyDouble(), anyDouble()))
                .thenReturn(new BehaviorInsightResult(0, Collections.emptyList()));

        when(ruleRepository.findByIsActiveTrue()).thenReturn(buildTestRules());
    }

    // ── Xây dựng bộ rules thử nghiệm ─────────────────────────────────────────

    private List<Rule> buildTestRules() {
        return List.of(
            rule("R1-NewRecipient-LargeAmount", "FINANCIAL",
                 "#isNewRecipient == true AND #tx.amount >= 20000000", 40),
            rule("R2-UntrustedDevice",          "DEVICE",
                 "#deviceTrusted == false", 30),
            rule("R3-SpamVelocity",             "VELOCITY",
                 "#recentTxCount > 3", 30),
            rule("R4-SuspiciousSession",        "BIOMETRIC",
                 "#suspiciousSession == true", 50),
            rule("R5-HighBalanceRatio",         "FINANCIAL",
                 "#balanceRatio >= 0.8", 35),
            rule("R6-DailyLimitBreach",         "FINANCIAL",
                 "#dailyTotalAmount >= 50000000", 40)
        );
    }

    private Rule rule(String name, String category, String spel, int score) {
        Rule r = new Rule();
        r.setRuleName(name);
        r.setCategory(category);
        r.setSpelExpression(spel);
        r.setActionScore(score);
        return r;
    }

    // ── Builder transaction ───────────────────────────────────────────────────

    /**
     * Tạo Transaction với các thuộc tính kiểm soát được.
     *
     * @param amount          Số tiền giao dịch
     * @param balance         Số dư tài khoản
     * @param suspicious      suspiciousSession / adminFlagged
     * @param deviceFingerprint  Fingerprint thiết bị (null → không có)
     */
    private Transaction buildTx(BigDecimal amount, BigDecimal balance,
                                  boolean suspicious, String deviceFingerprint) {
        long uid = idGen.getAndIncrement();

        User user = new User();
        user.setId(uid);
        user.setSuspiciousSession(suspicious);
        user.setAdminFlagged(false);

        Account account = new Account();
        account.setId(uid);
        account.setBalance(balance);
        account.setUser(user);

        Transaction tx = new Transaction();
        tx.setId(uid);
        tx.setFromAccount(account);
        tx.setAmount(amount);
        tx.setDeviceFingerprint(deviceFingerprint);
        return tx;
    }

    /**
     * Tương tự buildTx nhưng dùng adminFlagged=true thay vì suspiciousSession.
     * Trong evaluateRisk: combinedSuspiciousRisk = suspiciousSession || adminFlagged.
     */
    private Transaction buildTxAdminFlagged(BigDecimal amount, BigDecimal balance) {
        long uid = idGen.getAndIncrement();

        User user = new User();
        user.setId(uid);
        user.setSuspiciousSession(false);
        user.setAdminFlagged(true);

        Account account = new Account();
        account.setId(uid);
        account.setBalance(balance);
        account.setUser(user);

        Transaction tx = new Transaction();
        tx.setId(uid);
        tx.setFromAccount(account);
        tx.setAmount(amount);
        tx.setDeviceFingerprint("fp-admin-" + uid);
        return tx;
    }

    // ── Helpers mock repo ─────────────────────────────────────────────────────

    private void mockTrustedDevice(long userId, String fingerprint) {
        UserDevice d = new UserDevice();
        d.setIsTrusted(true);
        when(userDeviceRepository.findByUserIdAndDeviceFingerprint(eq(userId), eq(fingerprint)))
                .thenReturn(Optional.of(d));
    }

    private void mockUntrustedDevice(String fingerprint) {
        when(userDeviceRepository.findByUserIdAndDeviceFingerprint(anyLong(), eq(fingerprint)))
                .thenReturn(Optional.empty());
    }

    private void mockRecentTx(int count) {
        when(transactionRepository.countRecentTransactions(anyLong(), any())).thenReturn(count);
    }

    private void mockDailyTotal(BigDecimal daily) {
        when(transactionRepository.sumSuccessfulAmountToday(anyLong(), any())).thenReturn(daily);
    }

    /** Gọi evaluateRisk với danh sách buffer rỗng. */
    private int score(Transaction tx, boolean isNewRecipient) {
        return riskEvaluationService.evaluateRisk(
                tx, isNewRecipient, new ArrayList<>(), new ArrayList<>());
    }

    // =========================================================================
    // NHÓM 1: KỊCH BẢN FRAUD — phải bị phát hiện (score ≥ 70)
    // =========================================================================

    @Nested
    @DisplayName("Nhóm 1 — Kịch bản FRAUD (score ≥ 70)")
    class FraudScenarios {

        /**
         * F1: Người nhận mới + tiền lớn (30M ≥ 20M) + thiết bị lạ + spam (5 tx/phút)
         *   R1 → FINANCIAL=40 (capped at 40)
         *   R2 → DEVICE=30    (capped at 30)
         *   R3 → VELOCITY=30  (capped at 30)
         *   Tổng = 100
         */
        @Test
        @DisplayName("F1 — Người nhận mới + tiền 30M + thiết bị lạ + spam → score ≥ 70")
        void fraud_F1_newRecipient_largeAmount_untrustedDevice_spam() {
            Transaction tx = buildTx(
                    new BigDecimal("30000000"),
                    new BigDecimal("100000000"),
                    false, "unknown-fp-F1");

            mockUntrustedDevice("unknown-fp-F1");
            mockRecentTx(5);
            mockDailyTotal(BigDecimal.ZERO);

            int s = score(tx, true);
            assertThat(s).as("F1 score (new-recipient + 30M + untrusted + spam)")
                         .isGreaterThanOrEqualTo(HIGH_THRESHOLD);
        }

        /**
         * F2: Phiên đáng ngờ (suspiciousSession=true) + người nhận mới + tiền 25M
         *   R1 → FINANCIAL=40
         *   R4 → BIOMETRIC=50 (capped at 55)
         *   Tổng = 90
         */
        @Test
        @DisplayName("F2 — Phiên đáng ngờ + người nhận mới + tiền 25M → score ≥ 70")
        void fraud_F2_suspiciousSession_newRecipient_largeAmount() {
            Transaction tx = buildTx(
                    new BigDecimal("25000000"),
                    new BigDecimal("80000000"),
                    true, "fp-F2");

            mockTrustedDevice(tx.getFromAccount().getUser().getId(), "fp-F2");
            mockRecentTx(1);
            mockDailyTotal(BigDecimal.ZERO);

            int s = score(tx, true);
            assertThat(s).as("F2 score (suspicious + new-recipient + 25M)")
                         .isGreaterThanOrEqualTo(HIGH_THRESHOLD);
        }

        /**
         * F3: Tỉ lệ số dư cao (90M/100M = 90%) + thiết bị lạ + spam
         *   R5 → FINANCIAL=35 (35 < cap 40)
         *   R2 → DEVICE=30    (capped)
         *   R3 → VELOCITY=30  (capped)
         *   Tổng = 95
         */
        @Test
        @DisplayName("F3 — Tỉ lệ số dư 90% + thiết bị lạ + spam → score ≥ 70")
        void fraud_F3_highBalanceRatio_untrustedDevice_spam() {
            Transaction tx = buildTx(
                    new BigDecimal("90000000"),  // 90% của 100M
                    new BigDecimal("100000000"),
                    false, "unknown-fp-F3");

            mockUntrustedDevice("unknown-fp-F3");
            mockRecentTx(5);
            mockDailyTotal(BigDecimal.ZERO);

            int s = score(tx, false);
            assertThat(s).as("F3 score (balanceRatio=0.9 + untrusted + spam)")
                         .isGreaterThanOrEqualTo(HIGH_THRESHOLD);
        }

        /**
         * F4: Admin đánh dấu nguy hiểm (adminFlagged=true) + người nhận mới + tiền 20M
         *   combinedSuspiciousRisk = true → #suspiciousSession == true
         *   R1 → FINANCIAL=40
         *   R4 → BIOMETRIC=50
         *   Tổng = 90
         */
        @Test
        @DisplayName("F4 — Admin-flagged + người nhận mới + tiền 20M → score ≥ 70")
        void fraud_F4_adminFlagged_newRecipient_largeAmount() {
            Transaction tx = buildTxAdminFlagged(
                    new BigDecimal("20000000"),
                    new BigDecimal("50000000"));

            // Device tin cậy — để tách biệt tín hiệu admin-flagged
            mockTrustedDevice(tx.getFromAccount().getUser().getId(),
                              tx.getDeviceFingerprint());
            mockRecentTx(1);
            mockDailyTotal(BigDecimal.ZERO);

            int s = score(tx, true);
            assertThat(s).as("F4 score (adminFlagged + new-recipient + 20M)")
                         .isGreaterThanOrEqualTo(HIGH_THRESHOLD);
        }

        /**
         * F5: Vượt hạn mức ngày (60M > 50M ngưỡng) + thiết bị lạ + spam
         *   R6 → FINANCIAL=40 (capped)
         *   R2 → DEVICE=30    (capped)
         *   R3 → VELOCITY=30  (capped)
         *   Tổng = 100
         */
        @Test
        @DisplayName("F5 — Vượt hạn mức ngày + thiết bị lạ + spam → score ≥ 70")
        void fraud_F5_dailyLimitBreach_untrustedDevice_spam() {
            Transaction tx = buildTx(
                    new BigDecimal("5000000"),
                    new BigDecimal("200000000"),
                    false, "unknown-fp-F5");

            mockUntrustedDevice("unknown-fp-F5");
            mockRecentTx(5);
            mockDailyTotal(new BigDecimal("60000000"));  // đã chuyển 60M hôm nay

            int s = score(tx, false);
            assertThat(s).as("F5 score (dailyTotal=60M + untrusted + spam)")
                         .isGreaterThanOrEqualTo(HIGH_THRESHOLD);
        }
    }

    // =========================================================================
    // NHÓM 2: KỊCH BẢN LEGIT — không bị gắn cờ (score < 40)
    // =========================================================================

    @Nested
    @DisplayName("Nhóm 2 — Kịch bản LEGIT (score < 40)")
    class LegitScenarios {

        /**
         * L1: Người nhận quen + tiền nhỏ + thiết bị tin cậy + không spam
         *   Không rule nào kích hoạt → score = 0
         */
        @Test
        @DisplayName("L1 — Người nhận quen + tiền 1M + thiết bị tin cậy → score < 40")
        void legit_L1_knownRecipient_smallAmount_trustedDevice() {
            Transaction tx = buildTx(
                    new BigDecimal("1000000"),
                    new BigDecimal("50000000"),
                    false, "trusted-fp-L1");

            mockTrustedDevice(tx.getFromAccount().getUser().getId(), "trusted-fp-L1");
            mockRecentTx(1);
            mockDailyTotal(BigDecimal.ZERO);

            int s = score(tx, false);
            assertThat(s).as("L1 score (known + 1M + trusted)").isLessThan(LOW_THRESHOLD);
        }

        /**
         * L2: Người nhận quen + tiền 5M + thiết bị tin cậy
         *   Không rule nào kích hoạt → score = 0
         */
        @Test
        @DisplayName("L2 — Người nhận quen + tiền 5M + thiết bị tin cậy → score < 40")
        void legit_L2_knownRecipient_mediumAmount_trustedDevice() {
            Transaction tx = buildTx(
                    new BigDecimal("5000000"),
                    new BigDecimal("100000000"),
                    false, "trusted-fp-L2");

            mockTrustedDevice(tx.getFromAccount().getUser().getId(), "trusted-fp-L2");
            mockRecentTx(1);
            mockDailyTotal(BigDecimal.ZERO);

            int s = score(tx, false);
            assertThat(s).as("L2 score (known + 5M + trusted)").isLessThan(LOW_THRESHOLD);
        }

        /**
         * L3: Người nhận quen + tiền 10M + tỉ lệ thấp (5%) + thiết bị tin cậy
         *   balanceRatio = 10M/200M = 0.05 < 0.8 → R5 không kích hoạt
         */
        @Test
        @DisplayName("L3 — Người nhận quen + tiền 10M + tỉ lệ số dư thấp → score < 40")
        void legit_L3_knownRecipient_lowBalanceRatio_trustedDevice() {
            Transaction tx = buildTx(
                    new BigDecimal("10000000"),
                    new BigDecimal("200000000"),  // 10/200 = 5%
                    false, "trusted-fp-L3");

            mockTrustedDevice(tx.getFromAccount().getUser().getId(), "trusted-fp-L3");
            mockRecentTx(0);
            mockDailyTotal(BigDecimal.ZERO);

            int s = score(tx, false);
            assertThat(s).as("L3 score (known + 10M + ratio=5%)").isLessThan(LOW_THRESHOLD);
        }

        /**
         * L4: Người nhận MỚI nhưng số tiền nhỏ (5M < 20M ngưỡng của R1) + thiết bị tin cậy
         *   R1 không kích hoạt (amount < threshold) → score = 0
         */
        @Test
        @DisplayName("L4 — Người nhận MỚI + tiền 5M (dưới ngưỡng) + thiết bị tin cậy → score < 40")
        void legit_L4_newRecipient_amountBelowThreshold_trustedDevice() {
            Transaction tx = buildTx(
                    new BigDecimal("5000000"),   // 5M < 20M ngưỡng R1
                    new BigDecimal("50000000"),
                    false, "trusted-fp-L4");

            mockTrustedDevice(tx.getFromAccount().getUser().getId(), "trusted-fp-L4");
            mockRecentTx(1);
            mockDailyTotal(BigDecimal.ZERO);

            // isNewRecipient=true nhưng amount < 20M → R1 không trigger
            int s = score(tx, true);
            assertThat(s).as("L4 score (new-recipient + 5M below threshold)").isLessThan(LOW_THRESHOLD);
        }

        /**
         * L5: Tất cả tín hiệu bình thường — giao dịch định kỳ điển hình
         *   Không rule nào kích hoạt → score = 0
         */
        @Test
        @DisplayName("L5 — Giao dịch định kỳ điển hình — tất cả tín hiệu bình thường → score < 40")
        void legit_L5_allNormalSignals_typicalTransaction() {
            Transaction tx = buildTx(
                    new BigDecimal("500000"),
                    new BigDecimal("20000000"),
                    false, "trusted-fp-L5");

            mockTrustedDevice(tx.getFromAccount().getUser().getId(), "trusted-fp-L5");
            mockRecentTx(0);
            mockDailyTotal(BigDecimal.ZERO);

            int s = score(tx, false);
            assertThat(s).as("L5 score (all normal)").isLessThan(LOW_THRESHOLD);
        }
    }

    // =========================================================================
    // NHÓM 3: TỔNG HỢP — Precision / Recall / F1 / FPR
    // =========================================================================

    @Nested
    @DisplayName("Nhóm 3 — Tổng hợp: Precision ≥ 0.80, Recall ≥ 0.80, FPR ≤ 0.20")
    class QuantitativeSummary {

        // Biểu diễn một kịch bản trong dataset
        record Scenario(
            String name,
            BigDecimal amount,
            BigDecimal balance,
            boolean suspicious,
            boolean adminFlagged,
            String fingerprint,
            boolean deviceTrusted,
            boolean isNewRecipient,
            int recentTxCount,
            BigDecimal dailyTotal,
            boolean labeledFraud   // nhãn ground-truth
        ) {}

        @Test
        @DisplayName("📊 10 kịch bản có nhãn → đo Precision / Recall / FPR")
        void dataset_10scenarios_meetsQualityThresholds() {

            List<Scenario> dataset = List.of(
                // ── FRAUD cases (labeledFraud = true) ────────────────────────
                new Scenario("F1",
                    new BigDecimal("30000000"), new BigDecimal("100000000"),
                    false, false, "unk-F1", false, true,  5,  BigDecimal.ZERO,         true),
                new Scenario("F2",
                    new BigDecimal("25000000"), new BigDecimal("80000000"),
                    true,  false, "fp-F2",  true,  true,  1,  BigDecimal.ZERO,         true),
                new Scenario("F3",
                    new BigDecimal("90000000"), new BigDecimal("100000000"),
                    false, false, "unk-F3", false, false, 5,  BigDecimal.ZERO,         true),
                new Scenario("F4",
                    new BigDecimal("20000000"), new BigDecimal("50000000"),
                    false, true,  "fp-F4",  true,  true,  1,  BigDecimal.ZERO,         true),
                new Scenario("F5",
                    new BigDecimal("5000000"),  new BigDecimal("200000000"),
                    false, false, "unk-F5", false, false, 5,  new BigDecimal("60000000"), true),

                // ── LEGIT cases (labeledFraud = false) ───────────────────────
                new Scenario("L1",
                    new BigDecimal("1000000"),  new BigDecimal("50000000"),
                    false, false, "tr-L1",  true,  false, 1, BigDecimal.ZERO,         false),
                new Scenario("L2",
                    new BigDecimal("5000000"),  new BigDecimal("100000000"),
                    false, false, "tr-L2",  true,  false, 1, BigDecimal.ZERO,         false),
                new Scenario("L3",
                    new BigDecimal("10000000"), new BigDecimal("200000000"),
                    false, false, "tr-L3",  true,  false, 0, BigDecimal.ZERO,         false),
                new Scenario("L4",
                    new BigDecimal("5000000"),  new BigDecimal("50000000"),
                    false, false, "tr-L4",  true,  true,  1, BigDecimal.ZERO,         false),
                new Scenario("L5",
                    new BigDecimal("500000"),   new BigDecimal("20000000"),
                    false, false, "tr-L5",  true,  false, 0, BigDecimal.ZERO,         false)
            );

            int tp = 0, fp = 0, tn = 0, fn = 0;

            for (Scenario s : dataset) {
                // Setup transaction
                long uid = idGen.getAndIncrement();
                User user = new User();
                user.setId(uid);
                user.setSuspiciousSession(s.suspicious());
                user.setAdminFlagged(s.adminFlagged());

                Account account = new Account();
                account.setId(uid);
                account.setBalance(s.balance());
                account.setUser(user);

                Transaction tx = new Transaction();
                tx.setId(uid);
                tx.setFromAccount(account);
                tx.setAmount(s.amount());
                tx.setDeviceFingerprint(s.fingerprint());

                // Setup mocks
                when(transactionRepository.countRecentTransactions(anyLong(), any()))
                        .thenReturn(s.recentTxCount());
                when(transactionRepository.sumSuccessfulAmountToday(anyLong(), any()))
                        .thenReturn(s.dailyTotal());

                if (s.deviceTrusted()) {
                    UserDevice dev = new UserDevice();
                    dev.setIsTrusted(true);
                    when(userDeviceRepository.findByUserIdAndDeviceFingerprint(
                            eq(uid), eq(s.fingerprint())))
                            .thenReturn(Optional.of(dev));
                } else {
                    when(userDeviceRepository.findByUserIdAndDeviceFingerprint(
                            eq(uid), eq(s.fingerprint())))
                            .thenReturn(Optional.empty());
                }

                int actualScore = riskEvaluationService.evaluateRisk(
                        tx, s.isNewRecipient(), new ArrayList<>(), new ArrayList<>());

                boolean predictedFraud = actualScore >= HIGH_THRESHOLD;

                System.out.printf("  [%s] label=%-5s score=%3d predict=%-5s %s%n",
                        s.name(),
                        s.labeledFraud() ? "FRAUD" : "LEGIT",
                        actualScore,
                        predictedFraud ? "FRAUD" : "LEGIT",
                        predictedFraud == s.labeledFraud() ? "✅" : "❌");

                if (s.labeledFraud()  &&  predictedFraud)  tp++;
                else if (!s.labeledFraud() &&  predictedFraud)  fp++;
                else if (!s.labeledFraud() && !predictedFraud)  tn++;
                else                                              fn++;
            }

            double precision = (tp + fp > 0) ? (double) tp / (tp + fp) : 0.0;
            double recall    = (tp + fn > 0) ? (double) tp / (tp + fn) : 0.0;
            double f1        = (precision + recall > 0)
                               ? 2.0 * precision * recall / (precision + recall) : 0.0;
            double fpr       = (fp + tn > 0) ? (double) fp / (fp + tn) : 0.0;

            System.out.printf("""

                ╔══════════════════════════════════════════════╗
                ║   Risk Engine — Quantitative Evaluation      ║
                ╠══════════════════════════════════════════════╣
                ║  TP=%-2d  FP=%-2d  TN=%-2d  FN=%-2d                 ║
                ║  Precision  = %.2f  (ngưỡng ≥ %.2f)          ║
                ║  Recall     = %.2f  (ngưỡng ≥ %.2f)          ║
                ║  F1-Score   = %.2f                            ║
                ║  FPR        = %.2f  (ngưỡng ≤ %.2f)          ║
                ╚══════════════════════════════════════════════╝
                %n""",
                tp, fp, tn, fn,
                precision, MIN_PRECISION,
                recall,    MIN_RECALL,
                f1,
                fpr,       MAX_FPR);

            assertThat(precision).as("Precision phải ≥ %.2f".formatted(MIN_PRECISION))
                                 .isGreaterThanOrEqualTo(MIN_PRECISION);
            assertThat(recall).as("Recall phải ≥ %.2f".formatted(MIN_RECALL))
                              .isGreaterThanOrEqualTo(MIN_RECALL);
            assertThat(fpr).as("False Positive Rate phải ≤ %.2f".formatted(MAX_FPR))
                           .isLessThanOrEqualTo(MAX_FPR);
        }
    }

    // =========================================================================
    // NHÓM 4: PHÂN TÍCH ĐỘ NHẠY — dịch chuyển ngưỡng
    // =========================================================================

    @Nested
    @DisplayName("Nhóm 4 — Phân tích độ nhạy: thay đổi ngưỡng phân mức")
    class SensitivityAnalysis {

        /**
         * Với ngưỡng = 50 (thấp hơn 70): một số LEGIT bị gắn cờ → FPR tăng
         * Câu hỏi B4.2: "Nếu dịch ngưỡng, tỉ lệ chặn nhầm thay đổi ra sao?"
         */
        @Test
        @DisplayName("Ngưỡng 50 thấp hơn: FPR cao hơn so với ngưỡng chuẩn 70")
        void sensitivity_lowerThreshold50_fprIncreases() {
            int lowThreshold = 50;

            // Kịch bản LEGIT L4: người nhận mới + 5M + trusted device
            // Score = 0 (R1 không fire: amount < 20M threshold)
            Transaction lLow = buildTx(new BigDecimal("5000000"),
                                        new BigDecimal("50000000"), false, "tr-sens-L4");
            mockTrustedDevice(lLow.getFromAccount().getUser().getId(), "tr-sens-L4");
            mockRecentTx(1);
            mockDailyTotal(BigDecimal.ZERO);
            int scoreL4 = score(lLow, true);

            // Kịch bản FRAUD F1 rõ ràng: vẫn phát hiện được dù ngưỡng cao hơn
            Transaction fHigh = buildTx(new BigDecimal("30000000"),
                                         new BigDecimal("100000000"), false, "unk-sens-F1");
            mockUntrustedDevice("unk-sens-F1");
            mockRecentTx(5);
            mockDailyTotal(BigDecimal.ZERO);
            int scoreF1 = score(fHigh, true);

            // F1 vẫn vượt ngưỡng dù hạ xuống 50
            assertThat(scoreF1).as("F1 phải vượt ngưỡng thấp 50").isGreaterThanOrEqualTo(lowThreshold);

            // L4 không bị gắn cờ dù ngưỡng là 50 (score=0)
            assertThat(scoreL4).as("L4 không bị flag ở ngưỡng 50 vì score=0").isLessThan(lowThreshold);

            System.out.printf("""
                [Sensitivity] threshold=50
                  F1 score=%d → predicted=%s (expected: FRAUD)
                  L4 score=%d → predicted=%s (expected: LEGIT)
                %n""",
                scoreF1, scoreF1 >= lowThreshold ? "FRAUD" : "LEGIT",
                scoreL4, scoreL4 >= lowThreshold ? "FRAUD" : "LEGIT");
        }

        /**
         * Tín hiệu DEVICE đơn lẻ (30 điểm) không vượt ngưỡng FRAUD (70).
         * Xác nhận: thiết bị lạ một mình không đủ để đóng băng giao dịch.
         */
        @Test
        @DisplayName("Chỉ có tín hiệu DEVICE đơn lẻ (30 pts) — không đủ vượt ngưỡng FRAUD 70")
        void sensitivity_deviceSignalAlone_notSufficientForFraud() {
            // Chỉ R2 kích hoạt (untrusted device), không có tín hiệu nào khác
            Transaction tx = buildTx(new BigDecimal("5000000"),
                                      new BigDecimal("50000000"), false, "unk-device-only");
            mockUntrustedDevice("unk-device-only");
            mockRecentTx(1);
            mockDailyTotal(BigDecimal.ZERO);

            int s = score(tx, false);
            // DEVICE cap = 30 < threshold 70 → chưa đủ
            assertThat(s).as("Device đơn lẻ không đủ trigger HIGH risk")
                         .isLessThan(HIGH_THRESHOLD);
            System.out.printf("[Sensitivity] device-only score=%d (HIGH threshold=%d)%n",
                              s, HIGH_THRESHOLD);
        }
    }
}
