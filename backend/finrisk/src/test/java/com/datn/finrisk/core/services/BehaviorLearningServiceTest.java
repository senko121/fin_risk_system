package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserBehaviorProfile;
import com.datn.finrisk.core.repository.UserBehaviorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BehaviorLearningService — Unit Tests")
class BehaviorLearningServiceTest {

    @Mock private UserBehaviorProfileRepository profileRepository;
    @InjectMocks private BehaviorLearningService service;

    private Transaction buildTx(BigDecimal amount) {
        User user = new User();
        user.setId(7L);

        Account account = new Account();
        account.setUser(user);

        Transaction tx = new Transaction();
        tx.setId(100L);
        tx.setFromAccount(account);
        tx.setAmount(amount);
        tx.setCreatedAt(LocalDateTime.of(2026, 5, 29, 14, 30));
        return tx;
    }

    private UserBehaviorProfile buildProfile(User user, int txCount) {
        UserBehaviorProfile p = new UserBehaviorProfile();
        p.setUser(user);
        p.setTxCount(txCount);
        p.setMeanVector(new ArrayList<>(Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0)));
        p.setEwmaMeanVector(new ArrayList<>(Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0)));
        p.setEwmaVariance(new ArrayList<>(Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0)));
        java.util.List<java.util.List<Double>> cov = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            cov.add(new ArrayList<>(Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0)));
        }
        p.setCovarianceMatrixC(cov);
        return p;
    }

    // ── learnFromTransactionSync ──────────────────────────────────────────────

    @Nested
    @DisplayName("learnFromTransactionSync()")
    class LearnSync {

        @Test
        @DisplayName("new user (no profile) → creates profile and increments txCount to 1")
        void newUser_createsProfileWithTxCount1() {
            Transaction tx = buildTx(new BigDecimal("500000"));
            when(profileRepository.findByUserId(7L)).thenReturn(Optional.empty());
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.learnFromTransactionSync(tx, false, 86400.0);

            ArgumentCaptor<UserBehaviorProfile> captor = ArgumentCaptor.forClass(UserBehaviorProfile.class);
            verify(profileRepository).save(captor.capture());
            assertThat(captor.getValue().getTxCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("existing profile → txCount incremented by 1")
        void existingProfile_txCountIncremented() {
            Transaction tx  = buildTx(new BigDecimal("100000"));
            User user       = tx.getFromAccount().getUser();
            UserBehaviorProfile existing = buildProfile(user, 5);

            when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(existing));
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.learnFromTransactionSync(tx, false, 3600.0);

            assertThat(existing.getTxCount()).isEqualTo(6);
        }

        @Test
        @DisplayName("isNewRecipient=true → recipientNovelty=1.0 reflected in mean vector")
        void newRecipient_reflectedInMeanVector() {
            Transaction tx = buildTx(new BigDecimal("200000"));
            User user      = tx.getFromAccount().getUser();
            UserBehaviorProfile profile = buildProfile(user, 0);

            when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.learnFromTransactionSync(tx, true, 86400.0);

            // After 1 tx with isNewRecipient=true, meanVector[4] should be 1.0
            assertThat(profile.getMeanVector().get(4)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("isNewRecipient=false → recipientNovelty=0 in mean vector")
        void knownRecipient_zeroNoveltyInMean() {
            Transaction tx = buildTx(new BigDecimal("200000"));
            User user      = tx.getFromAccount().getUser();
            UserBehaviorProfile profile = buildProfile(user, 0);

            when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.learnFromTransactionSync(tx, false, 86400.0);

            assertThat(profile.getMeanVector().get(4)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("profile is always persisted after learning")
        void profileAlwaysSaved() {
            Transaction tx = buildTx(new BigDecimal("50000"));
            User user      = tx.getFromAccount().getUser();
            UserBehaviorProfile profile = buildProfile(user, 10);

            when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.learnFromTransactionSync(tx, false, 1800.0);

            verify(profileRepository).save(profile);
        }

        @Test
        @DisplayName("EWMA initialized on first transaction (txCount becomes 1)")
        void ewmaInitialized_firstTransaction() {
            Transaction tx = buildTx(new BigDecimal("300000"));
            User user      = tx.getFromAccount().getUser();
            UserBehaviorProfile profile = buildProfile(user, 0);

            when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.learnFromTransactionSync(tx, false, 86400.0);

            // After 1st tx, ewmaVariance should be 0 (no variance yet)
            assertThat(profile.getEwmaVariance().get(0)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("lastTxTimestamp is updated after learning")
        void lastTxTimestamp_updated() {
            Transaction tx = buildTx(new BigDecimal("100000"));
            User user      = tx.getFromAccount().getUser();
            UserBehaviorProfile profile = buildProfile(user, 2);

            when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.learnFromTransactionSync(tx, false, 3600.0);

            assertThat(profile.getLastTxTimestamp()).isNotNull();
        }
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("zero amount transaction → no exception, profile saved")
        void zeroAmount_noException() {
            Transaction tx = buildTx(BigDecimal.ZERO);
            User user      = tx.getFromAccount().getUser();

            when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(buildProfile(user, 0)));
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> service.learnFromTransactionSync(tx, false, 86400.0))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("very large amount → log-normalized, no NaN or Infinity in mean vector")
        void largeAmount_noNanInMeanVector() {
            Transaction tx = buildTx(new BigDecimal("999999999999"));
            User user      = tx.getFromAccount().getUser();
            UserBehaviorProfile profile = buildProfile(user, 0);

            when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.learnFromTransactionSync(tx, false, 86400.0);

            for (Double v : profile.getMeanVector()) {
                assertThat(v).isNotNaN().isFinite();
            }
        }

        @Test
        @DisplayName("gapSeconds < 1 → clamped to 1 in log transform")
        void negativeGapSeconds_noException() {
            Transaction tx = buildTx(new BigDecimal("100000"));
            User user      = tx.getFromAccount().getUser();
            UserBehaviorProfile profile = buildProfile(user, 0);

            when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> service.learnFromTransactionSync(tx, false, -50.0))
                    .doesNotThrowAnyException();
        }
    }
}
