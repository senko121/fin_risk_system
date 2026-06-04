package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.BehaviorInsightResult;
import com.datn.finrisk.application.dtos.BehaviorProfileDTO;
import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserBehaviorProfile;
import com.datn.finrisk.core.repository.UserBehaviorProfileRepository;
import com.datn.finrisk.core.utils.MahalanobisCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BehavioralProfilingService — Unit Tests")
class BehavioralProfilingServiceTest {

    @Mock private MahalanobisCalculator              mathCalculator;
    @Mock private UserBehaviorProfileRepository      profileRepository;

    @InjectMocks private BehavioralProfilingService service;

    // ── helpers ───────────────────────────────────────────────────────────────

    private Transaction buildTx(BigDecimal amount, int hour) {
        User user = new User();
        user.setId(1L);

        Account account = new Account();
        account.setUser(user);

        Transaction tx = new Transaction();
        tx.setId(100L);
        tx.setFromAccount(account);
        tx.setAmount(amount);
        tx.setCreatedAt(LocalDateTime.of(2026, 5, 29, hour, 30));
        return tx;
    }

    private UserBehaviorProfile buildProfile(int txCount) {
        UserBehaviorProfile p = new UserBehaviorProfile();
        p.setTxCount(txCount);
        p.setMeanVector(new ArrayList<>(List.of(13.0, 0.5, 0.5, 8.0, 0.1)));
        p.setEwmaMeanVector(new ArrayList<>(List.of(13.0, 0.5, 0.5, 8.0, 0.1)));
        p.setEwmaVariance(new ArrayList<>(List.of(0.5, 0.1, 0.1, 0.5, 0.05)));
        List<List<Double>> cov = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            List<Double> row = new ArrayList<>(List.of(1.0, 0.0, 0.0, 0.0, 0.0));
            cov.add(row);
        }
        p.setCovarianceMatrixC(cov);
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    // ── calculateBehavioralAnomalyScore — cold start ──────────────────────────

    @Nested
    @DisplayName("calculateBehavioralAnomalyScore() — cold start (<5 tx)")
    class ColdStart {

        @Test
        @DisplayName("null profile → returns score=0 with COLD_START insight")
        void nullProfile_returns0() {
            Transaction tx = buildTx(new BigDecimal("500000"), 10);

            BehaviorInsightResult result =
                    service.calculateBehavioralAnomalyScore(tx, null, 3600.0, 0.0);

            assertThat(result.getTotalScore()).isEqualTo(0);
            assertThat(result.getInsights()).isNotEmpty();
            assertThat(result.getInsights().get(0).getFeatureName()).isEqualTo("COLD_START");
        }

        @Test
        @DisplayName("profile with txCount=4 → returns score=0 (not enough data)")
        void txCount4_returns0() {
            Transaction tx = buildTx(new BigDecimal("200000"), 14);
            UserBehaviorProfile profile = buildProfile(4);

            BehaviorInsightResult result =
                    service.calculateBehavioralAnomalyScore(tx, profile, 1800.0, 0.0);

            assertThat(result.getTotalScore()).isEqualTo(0);
            assertThat(result.getInsights().get(0).getFeatureName()).isEqualTo("COLD_START");
        }

        @Test
        @DisplayName("cold start insight message mentions current tx count")
        void coldStartInsight_mentionsTxCount() {
            Transaction tx = buildTx(new BigDecimal("100000"), 9);
            UserBehaviorProfile profile = buildProfile(3);

            BehaviorInsightResult result =
                    service.calculateBehavioralAnomalyScore(tx, profile, 3600.0, 0.0);

            String message = result.getInsights().get(0).getInsightMessage();
            assertThat(message).contains("3");
        }
    }

    // ── calculateBehavioralAnomalyScore — Euclidean phase (5-49 tx) ──────────

    @Nested
    @DisplayName("calculateBehavioralAnomalyScore() — Euclidean phase (5–49 tx)")
    class EuclideanPhase {

        @Test
        @DisplayName("calls calculateEuclidean once, not Mahalanobis")
        void callsEuclidean_notMahalanobis() {
            Transaction tx = buildTx(new BigDecimal("500000"), 10);
            UserBehaviorProfile profile = buildProfile(10);
            when(mathCalculator.calculateEuclidean(any(double[].class), any(double[].class)))
                    .thenReturn(1.0);

            service.calculateBehavioralAnomalyScore(tx, profile, 3600.0, 0.0);

            verify(mathCalculator, times(1)).calculateEuclidean(any(), any());
            verify(mathCalculator, never()).calculateMahalanobis(
                    any(), any(), any(), anyInt(), anyDouble());
        }

        @Test
        @DisplayName("score is normalized from Euclidean distance (raw=4 → score=100)")
        void euclideanRaw4_score100() {
            Transaction tx = buildTx(new BigDecimal("500000"), 10);
            UserBehaviorProfile profile = buildProfile(20);
            when(mathCalculator.calculateEuclidean(any(double[].class), any(double[].class)))
                    .thenReturn(4.0); // normalizeEuclidean(4) = min(4/4.0*100, 100) = 100

            BehaviorInsightResult result =
                    service.calculateBehavioralAnomalyScore(tx, profile, 3600.0, 0.0);

            assertThat(result.getTotalScore()).isEqualTo(100);
        }

        @Test
        @DisplayName("score=0 when Euclidean distance is 0 (identical to mean)")
        void euclideanRaw0_score0() {
            Transaction tx = buildTx(new BigDecimal("500000"), 10);
            UserBehaviorProfile profile = buildProfile(30);
            when(mathCalculator.calculateEuclidean(any(double[].class), any(double[].class)))
                    .thenReturn(0.0);

            BehaviorInsightResult result =
                    service.calculateBehavioralAnomalyScore(tx, profile, 3600.0, 0.0);

            assertThat(result.getTotalScore()).isEqualTo(0);
        }

        @Test
        @DisplayName("result has 5 insights (AMOUNT + TIME + FREQUENCY + RECIPIENT + BEHAVIOR_SCORE)")
        void resultHas5Insights() {
            Transaction tx = buildTx(new BigDecimal("300000"), 15);
            UserBehaviorProfile profile = buildProfile(25);
            when(mathCalculator.calculateEuclidean(any(), any())).thenReturn(1.0);

            BehaviorInsightResult result =
                    service.calculateBehavioralAnomalyScore(tx, profile, 3600.0, 0.0);

            assertThat(result.getInsights()).hasSize(5);
        }
    }

    // ── calculateBehavioralAnomalyScore — Transition phase (50-150 tx) ────────

    @Nested
    @DisplayName("calculateBehavioralAnomalyScore() — Transition phase (50–150 tx)")
    class TransitionPhase {

        @Test
        @DisplayName("calls both Euclidean and Mahalanobis for blending")
        void callsBothCalculators() {
            Transaction tx = buildTx(new BigDecimal("500000"), 10);
            UserBehaviorProfile profile = buildProfile(100);
            when(mathCalculator.calculateEuclidean(any(), any())).thenReturn(2.0);
            when(mathCalculator.calculateMahalanobis(any(), any(), any(), anyInt(), anyDouble()))
                    .thenReturn(5.0);

            service.calculateBehavioralAnomalyScore(tx, profile, 3600.0, 0.0);

            verify(mathCalculator).calculateEuclidean(any(), any());
            verify(mathCalculator).calculateMahalanobis(any(), any(), any(), eq(100), eq(0.1));
        }

        @Test
        @DisplayName("score is blended (txCount=50 → 100% Euclidean)")
        void txCount50_pureEuclidean() {
            Transaction tx = buildTx(new BigDecimal("500000"), 10);
            UserBehaviorProfile profile = buildProfile(50);
            // weightMaha = (50-50)/100 = 0 → pure Euclidean
            when(mathCalculator.calculateEuclidean(any(), any())).thenReturn(2.0);
            when(mathCalculator.calculateMahalanobis(any(), any(), any(), anyInt(), anyDouble()))
                    .thenReturn(0.0);

            BehaviorInsightResult result =
                    service.calculateBehavioralAnomalyScore(tx, profile, 3600.0, 0.0);

            int expected = (int) Math.round(Math.min((2.0 / 4.0) * 100, 100)); // = 50
            assertThat(result.getTotalScore()).isEqualTo(expected);
        }

        @Test
        @DisplayName("score is blended (txCount=150 → 100% Mahalanobis)")
        void txCount150_pureMahalanobis() {
            Transaction tx = buildTx(new BigDecimal("500000"), 10);
            UserBehaviorProfile profile = buildProfile(150);
            // weightMaha = (150-50)/100 = 1 → pure Mahalanobis
            when(mathCalculator.calculateEuclidean(any(), any())).thenReturn(0.0);
            when(mathCalculator.calculateMahalanobis(any(), any(), any(), anyInt(), anyDouble()))
                    .thenReturn(10.0); // normalizeMahalanobis(10) = min(10/20.51*100, 100) ≈ 48

            BehaviorInsightResult result =
                    service.calculateBehavioralAnomalyScore(tx, profile, 3600.0, 0.0);

            assertThat(result.getTotalScore()).isGreaterThanOrEqualTo(0).isLessThanOrEqualTo(100);
        }
    }

    // ── calculateBehavioralAnomalyScore — Mature phase (>150 tx) ─────────────

    @Nested
    @DisplayName("calculateBehavioralAnomalyScore() — Mature phase (>150 tx)")
    class MaturePhase {

        @Test
        @DisplayName("calls only Mahalanobis with lambda=0.001")
        void callsOnlyMahalanobis() {
            Transaction tx = buildTx(new BigDecimal("500000"), 10);
            UserBehaviorProfile profile = buildProfile(200);
            when(mathCalculator.calculateMahalanobis(any(), any(), any(), anyInt(), anyDouble()))
                    .thenReturn(8.0);

            service.calculateBehavioralAnomalyScore(tx, profile, 3600.0, 0.0);

            verify(mathCalculator, never()).calculateEuclidean(any(), any());
            verify(mathCalculator).calculateMahalanobis(any(), any(), any(), eq(200), eq(0.001));
        }

        @Test
        @DisplayName("score capped at 100 even with extreme Mahalanobis")
        void extremeMahalanobis_scoreCappedAt100() {
            Transaction tx = buildTx(new BigDecimal("9999999999"), 2);
            UserBehaviorProfile profile = buildProfile(300);
            when(mathCalculator.calculateMahalanobis(any(), any(), any(), anyInt(), anyDouble()))
                    .thenReturn(10000.0);

            BehaviorInsightResult result =
                    service.calculateBehavioralAnomalyScore(tx, profile, 3600.0, 1.0);

            assertThat(result.getTotalScore()).isEqualTo(100);
        }

        @Test
        @DisplayName("score=0 when Mahalanobis returns 0 (identical to profile)")
        void mahalanobis0_score0() {
            Transaction tx = buildTx(new BigDecimal("500000"), 10);
            UserBehaviorProfile profile = buildProfile(200);
            when(mathCalculator.calculateMahalanobis(any(), any(), any(), anyInt(), anyDouble()))
                    .thenReturn(0.0);

            BehaviorInsightResult result =
                    service.calculateBehavioralAnomalyScore(tx, profile, 3600.0, 0.0);

            assertThat(result.getTotalScore()).isEqualTo(0);
        }
    }

    // ── calculateBehavioralAnomalyScore — insight quality ────────────────────

    @Nested
    @DisplayName("calculateBehavioralAnomalyScore() — insight quality")
    class InsightQuality {

        @Test
        @DisplayName("BEHAVIOR_SCORE insight is HIDDEN type")
        void behaviorScoreInsight_isHidden() {
            Transaction tx = buildTx(new BigDecimal("500000"), 10);
            UserBehaviorProfile profile = buildProfile(20);
            when(mathCalculator.calculateEuclidean(any(), any())).thenReturn(1.0);

            BehaviorInsightResult result =
                    service.calculateBehavioralAnomalyScore(tx, profile, 3600.0, 0.0);

            boolean hasHiddenBehaviorScore = result.getInsights().stream()
                    .anyMatch(i -> "BEHAVIOR_SCORE".equals(i.getFeatureName())
                            && "HIDDEN".equals(i.getInsightType()));
            assertThat(hasHiddenBehaviorScore).isTrue();
        }

        @Test
        @DisplayName("new recipient (novelty=1.0) → RECIPIENT insight has non-safe type at high z")
        void newRecipient_recipientInsightWarningOrDanger() {
            Transaction tx = buildTx(new BigDecimal("500000"), 10);
            UserBehaviorProfile profile = buildProfile(50);
            // profile mean[4]=0.1 (10% new recipients), variance[4]=0.05
            // recipientNovelty=1.0 → very anomalous
            when(mathCalculator.calculateEuclidean(any(), any())).thenReturn(1.0);
            when(mathCalculator.calculateMahalanobis(any(), any(), any(), anyInt(), anyDouble()))
                    .thenReturn(1.0);

            BehaviorInsightResult result =
                    service.calculateBehavioralAnomalyScore(tx, profile, 3600.0, 1.0);

            var recipientInsight = result.getInsights().stream()
                    .filter(i -> "RECIPIENT".equals(i.getFeatureName()))
                    .findFirst();
            assertThat(recipientInsight).isPresent();
            assertThat(recipientInsight.get().getInsightType())
                    .isIn("WARNING", "DANGER");
        }

        @Test
        @DisplayName("known recipient (novelty=0.0) → RECIPIENT insight is SAFE")
        void knownRecipient_recipientInsightSafe() {
            Transaction tx = buildTx(new BigDecimal("500000"), 10);
            UserBehaviorProfile profile = buildProfile(20);
            when(mathCalculator.calculateEuclidean(any(), any())).thenReturn(0.5);

            BehaviorInsightResult result =
                    service.calculateBehavioralAnomalyScore(tx, profile, 3600.0, 0.0);

            var recipientInsight = result.getInsights().stream()
                    .filter(i -> "RECIPIENT".equals(i.getFeatureName()))
                    .findFirst();
            assertThat(recipientInsight).isPresent();
            assertThat(recipientInsight.get().getInsightType()).isEqualTo("SAFE");
        }
    }

    // ── getReadableProfile ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getReadableProfile()")
    class GetReadableProfile {

        @Test
        @DisplayName("no profile in DB → returns empty DTO with COLD_START phase")
        void noProfile_emptyColdStart() {
            when(profileRepository.findByUserId(1L)).thenReturn(Optional.empty());

            BehaviorProfileDTO dto = service.getReadableProfile(1L);

            assertThat(dto.getUserId()).isEqualTo(1L);
            assertThat(dto.getProfilingPhase()).isEqualTo("COLD_START");
            assertThat(dto.getTxCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("profile with null meanVector → returns empty DTO")
        void nullMeanVector_emptyDTO() {
            UserBehaviorProfile profile = new UserBehaviorProfile();
            profile.setTxCount(3);
            profile.setMeanVector(null);

            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

            BehaviorProfileDTO dto = service.getReadableProfile(1L);
            assertThat(dto.getProfilingPhase()).isEqualTo("COLD_START");
        }

        @Test
        @DisplayName("profile with empty meanVector → returns empty DTO")
        void emptyMeanVector_emptyDTO() {
            UserBehaviorProfile profile = new UserBehaviorProfile();
            profile.setTxCount(5);
            profile.setMeanVector(new ArrayList<>());

            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

            BehaviorProfileDTO dto = service.getReadableProfile(1L);
            assertThat(dto.getProfilingPhase()).isEqualTo("COLD_START");
        }

        @Test
        @DisplayName("mature profile → returns full DTO with MATURE phase")
        void matureProfile_fullDTO() {
            UserBehaviorProfile profile = buildProfile(200);

            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

            BehaviorProfileDTO dto = service.getReadableProfile(1L);

            assertThat(dto.getUserId()).isEqualTo(1L);
            assertThat(dto.getProfilingPhase()).isEqualTo("MATURE");
            assertThat(dto.getTxCount()).isEqualTo(200);
            assertThat(dto.getProfileReliability()).isEqualTo(1.0); // min(200/150, 1.0)
            assertThat(dto.getCalculationMethod()).isEqualTo("MAHALANOBIS");
        }

        @Test
        @DisplayName("cold-start profile (txCount=30) → COLD_START phase, EUCLIDEAN method")
        void coldStartProfile_correctPhase() {
            UserBehaviorProfile profile = buildProfile(30);

            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

            BehaviorProfileDTO dto = service.getReadableProfile(1L);
            assertThat(dto.getProfilingPhase()).isEqualTo("COLD_START");
            assertThat(dto.getCalculationMethod()).isEqualTo("EUCLIDEAN");
        }

        @Test
        @DisplayName("transition profile (txCount=100) → TRANSITION phase, BLEND method")
        void transitionProfile_correctPhase() {
            UserBehaviorProfile profile = buildProfile(100);

            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

            BehaviorProfileDTO dto = service.getReadableProfile(1L);
            assertThat(dto.getProfilingPhase()).isEqualTo("TRANSITION");
            assertThat(dto.getCalculationMethod()).isEqualTo("BLEND");
        }

        @Test
        @DisplayName("anomalyLevel is NORMAL when anomalyScore < 40")
        void lowAnomalyScore_normalLevel() {
            UserBehaviorProfile profile = buildProfile(200);
            // identical mean and ewmaMean → anomalyScore = 0

            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

            BehaviorProfileDTO dto = service.getReadableProfile(1L);
            assertThat(dto.getAnomalyLevel()).isEqualTo("NORMAL");
        }
    }
}
