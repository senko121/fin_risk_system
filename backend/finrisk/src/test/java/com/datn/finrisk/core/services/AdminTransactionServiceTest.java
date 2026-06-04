package com.datn.finrisk.core.services;

import com.datn.finrisk.application.dtos.TransactionRiskDetailDTO;
import com.datn.finrisk.core.entities.*;
import com.datn.finrisk.core.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminTransactionService — Unit Tests")
class AdminTransactionServiceTest {

    @Mock private TransactionRepository           transactionRepository;
    @Mock private RiskScoreRepository             riskScoreRepository;
    @Mock private TransactionAiInsightRepository  aiInsightRepository;
    @Mock private UserBehaviorProfileRepository   userBehaviorProfileRepository;

    @InjectMocks private AdminTransactionService service;

    // ── helpers ───────────────────────────────────────────────────────────────

    private Transaction buildTx(Long id) {
        User user = new User();
        user.setId(10L);
        user.setFullName("Nguyen Van A");
        user.setUsername("nguyenvana");

        Account account = new Account();
        account.setUser(user);
        account.setAccountNumber("ACC001");

        Transaction tx = new Transaction();
        tx.setId(id);
        tx.setFromAccount(account);
        tx.setToAccountNumber("ACC002");
        tx.setToBankCode("INTERNAL");
        tx.setAmount(new BigDecimal("500000"));
        tx.setStatus("SUCCESS");
        tx.setRiskLevel("LOW");
        tx.setTotalRiskScore(5);
        tx.setCreatedAt(LocalDateTime.of(2026, 5, 29, 10, 0));
        return tx;
    }

    private UserBehaviorProfile buildMatureProfile(User user) {
        UserBehaviorProfile p = new UserBehaviorProfile();
        p.setUser(user);
        p.setTxCount(200);
        p.setMeanVector(new ArrayList<>(List.of(13.0, 0.0, 1.0, 10.0, 0.1)));
        p.setEwmaMeanVector(new ArrayList<>(List.of(13.0, 0.0, 1.0, 10.0, 0.1)));
        p.setEwmaVariance(new ArrayList<>(List.of(0.5, 0.1, 0.1, 0.5, 0.05)));
        List<List<Double>> cov = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            cov.add(new ArrayList<>(List.of(1.0, 0.0, 0.0, 0.0, 0.0)));
        }
        p.setCovarianceMatrixC(cov);
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    // ── getTransactions ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTransactions()")
    class GetTransactions {

        @Test
        @DisplayName("returns empty page when repository returns nothing")
        void emptyPage_returnedAsIs() {
            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<com.datn.finrisk.application.dtos.AdminTransactionDTO> result =
                    service.getTransactions(0, 10, null, null, null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("maps Transaction to AdminTransactionDTO with sender info")
        void mapsToDTO_withSenderInfo() {
            Transaction tx = buildTx(1L);
            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(tx)));
            when(transactionRepository.findRecipientNamesBulk(any()))
                    .thenReturn(List.of());

            Page<com.datn.finrisk.application.dtos.AdminTransactionDTO> result =
                    service.getTransactions(0, 10, null, null, null);

            assertThat(result).hasSize(1);
            var dto = result.getContent().get(0);
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getSenderAccountNumber()).isEqualTo("ACC001");
            assertThat(dto.getAmount()).isEqualByComparingTo("500000");
        }

        @Test
        @DisplayName("recipient name resolved from bulk map when found")
        void recipientNameFromBulkMap() {
            Transaction tx = buildTx(2L);
            List<Object[]> bulkResult = new ArrayList<>();
            bulkResult.add(new Object[]{"ACC002", "Tran Thi B"});
            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(tx)));
            when(transactionRepository.findRecipientNamesBulk(any()))
                    .thenReturn(bulkResult);

            var result = service.getTransactions(0, 10, null, null, null);
            assertThat(result.getContent().get(0).getRecipientFullName()).isEqualTo("Tran Thi B");
        }

        @Test
        @DisplayName("recipient name defaults to 'Người nhận ngoài hệ thống' when not in map")
        void recipientNameDefaultsWhenNotFound() {
            Transaction tx = buildTx(3L);
            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(tx)));
            when(transactionRepository.findRecipientNamesBulk(any()))
                    .thenReturn(List.of());

            var result = service.getTransactions(0, 10, null, null, null);
            assertThat(result.getContent().get(0).getRecipientFullName())
                    .isEqualTo("Người nhận ngoài hệ thống");
        }
    }

    // ── getTransactionRiskDetail ──────────────────────────────────────────────

    @Nested
    @DisplayName("getTransactionRiskDetail()")
    class GetTransactionRiskDetail {

        @Test
        @DisplayName("throws RuntimeException when transaction not found")
        void notFound_throwsException() {
            when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTransactionRiskDetail(999L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("returns DTO with id and basic fields when transaction exists")
        void found_returnsDTO() {
            Transaction tx = buildTx(1L);
            tx.setLocationIp("192.168.1.1");
            tx.setDeviceFingerprint("fp123");

            when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
            when(riskScoreRepository.findByTransactionIdInWithRule(List.of(1L)))
                    .thenReturn(List.of());
            when(aiInsightRepository.findByTransactionIdIn(List.of(1L)))
                    .thenReturn(List.of());
            when(userBehaviorProfileRepository.findByUserId(10L))
                    .thenReturn(Optional.empty());

            TransactionRiskDetailDTO result = service.getTransactionRiskDetail(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getLocationIp()).isEqualTo("192.168.1.1");
            assertThat(result.getDeviceFingerprint()).isEqualTo("fp123");
            assertThat(result.getTotalRiskScore()).isEqualTo(5);
        }

        @Test
        @DisplayName("categoryBreakdown is populated from risk scores")
        void categoryBreakdown_fromRiskScores() {
            Transaction tx = buildTx(1L);
            Rule rule = new Rule();
            rule.setCategory("FINANCIAL");

            RiskScore rs = new RiskScore();
            rs.setRule(rule);
            rs.setAppliedScore(15);

            when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
            when(riskScoreRepository.findByTransactionIdInWithRule(List.of(1L)))
                    .thenReturn(List.of(rs));
            when(aiInsightRepository.findByTransactionIdIn(List.of(1L)))
                    .thenReturn(List.of());
            when(userBehaviorProfileRepository.findByUserId(10L))
                    .thenReturn(Optional.empty());

            TransactionRiskDetailDTO result = service.getTransactionRiskDetail(1L);

            assertThat(result.getCategoryBreakdown()).containsKey("FINANCIAL");
            var fin = result.getCategoryBreakdown().get("FINANCIAL");
            assertThat(fin.getRaw()).isEqualTo(15);
            assertThat(fin.getEffective()).isLessThanOrEqualTo(40); // FINANCIAL cap = 40
        }

        @Test
        @DisplayName("risk score with null category falls back to CONTEXTUAL")
        void nullCategoryFallsToContextual() {
            Transaction tx = buildTx(1L);

            RiskScore rs = new RiskScore();
            rs.setRule(null); // null rule → null category → CONTEXTUAL
            rs.setAppliedScore(10);

            when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
            when(riskScoreRepository.findByTransactionIdInWithRule(List.of(1L)))
                    .thenReturn(List.of(rs));
            when(aiInsightRepository.findByTransactionIdIn(List.of(1L)))
                    .thenReturn(List.of());
            when(userBehaviorProfileRepository.findByUserId(10L))
                    .thenReturn(Optional.empty());

            TransactionRiskDetailDTO result = service.getTransactionRiskDetail(1L);

            assertThat(result.getCategoryBreakdown()).containsKey("CONTEXTUAL");
        }

        @Test
        @DisplayName("BEHAVIOR_SCORE insight sets behaviorScore on DTO")
        void behaviorScoreInsight_setsField() {
            Transaction tx = buildTx(1L);

            TransactionAiInsight behaviorInsight = new TransactionAiInsight(
                    null, "BEHAVIOR_SCORE", "Phase=MATURE | D²=5.00", "HIDDEN", 42.0);

            when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
            when(riskScoreRepository.findByTransactionIdInWithRule(List.of(1L)))
                    .thenReturn(List.of());
            when(aiInsightRepository.findByTransactionIdIn(List.of(1L)))
                    .thenReturn(List.of(behaviorInsight));
            when(userBehaviorProfileRepository.findByUserId(10L))
                    .thenReturn(Optional.empty());

            TransactionRiskDetailDTO result = service.getTransactionRiskDetail(1L);
            assertThat(result.getBehaviorScore()).isEqualTo(42);
        }

        @Test
        @DisplayName("HIDDEN insights are excluded from aiInsights list")
        void hiddenInsights_excludedFromList() {
            Transaction tx = buildTx(1L);

            TransactionAiInsight hidden = new TransactionAiInsight(
                    null, "SOME_DEBUG", "debug info", "HIDDEN", 0.0);
            TransactionAiInsight visible = new TransactionAiInsight(
                    null, "AMOUNT", "amount insight", "WARNING", 2.0);

            when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
            when(riskScoreRepository.findByTransactionIdInWithRule(List.of(1L)))
                    .thenReturn(List.of());
            when(aiInsightRepository.findByTransactionIdIn(List.of(1L)))
                    .thenReturn(List.of(hidden, visible));
            when(userBehaviorProfileRepository.findByUserId(10L))
                    .thenReturn(Optional.empty());

            TransactionRiskDetailDTO result = service.getTransactionRiskDetail(1L);
            assertThat(result.getAiInsights()).hasSize(1);
            assertThat(result.getAiInsights().get(0).getFeature()).isEqualTo("AMOUNT");
        }

        @Test
        @DisplayName("profileBaseline is null when no profile exists for sender")
        void noProfile_baselineIsNull() {
            Transaction tx = buildTx(1L);

            when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
            when(riskScoreRepository.findByTransactionIdInWithRule(any())).thenReturn(List.of());
            when(aiInsightRepository.findByTransactionIdIn(any())).thenReturn(List.of());
            when(userBehaviorProfileRepository.findByUserId(10L)).thenReturn(Optional.empty());

            TransactionRiskDetailDTO result = service.getTransactionRiskDetail(1L);
            assertThat(result.getProfileBaseline()).isNull();
        }

        @Test
        @DisplayName("profileBaseline is null when profile has txCount < 5")
        void lowTxCountProfile_baselineIsNull() {
            Transaction tx = buildTx(1L);

            UserBehaviorProfile profile = new UserBehaviorProfile();
            profile.setTxCount(3);

            when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
            when(riskScoreRepository.findByTransactionIdInWithRule(any())).thenReturn(List.of());
            when(aiInsightRepository.findByTransactionIdIn(any())).thenReturn(List.of());
            when(userBehaviorProfileRepository.findByUserId(10L)).thenReturn(Optional.of(profile));

            TransactionRiskDetailDTO result = service.getTransactionRiskDetail(1L);
            assertThat(result.getProfileBaseline()).isNull();
        }

        @Test
        @DisplayName("profileBaseline populated when mature profile available")
        void matureProfile_baselinePopulated() {
            Transaction tx = buildTx(1L);
            User user = tx.getFromAccount().getUser();
            UserBehaviorProfile profile = buildMatureProfile(user);

            when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
            when(riskScoreRepository.findByTransactionIdInWithRule(any())).thenReturn(List.of());
            when(aiInsightRepository.findByTransactionIdIn(any())).thenReturn(List.of());
            when(userBehaviorProfileRepository.findByUserId(10L)).thenReturn(Optional.of(profile));

            TransactionRiskDetailDTO result = service.getTransactionRiskDetail(1L);
            assertThat(result.getProfileBaseline()).isNotNull();
            assertThat(result.getProfileBaseline().getTxCount()).isEqualTo(200);
            assertThat(result.getProfileBaseline().getPhase()).isEqualTo("MATURE");
        }

        @Test
        @DisplayName("aiContribution is capped at AI_MAX_CONTRIBUTION (35)")
        void aiContribution_cappedAt35() {
            Transaction tx = buildTx(1L);
            tx.setTotalRiskScore(100);

            when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
            when(riskScoreRepository.findByTransactionIdInWithRule(any())).thenReturn(List.of());
            when(aiInsightRepository.findByTransactionIdIn(any())).thenReturn(List.of());
            when(userBehaviorProfileRepository.findByUserId(10L)).thenReturn(Optional.empty());

            TransactionRiskDetailDTO result = service.getTransactionRiskDetail(1L);
            assertThat(result.getAiContribution()).isLessThanOrEqualTo(35);
        }
    }
}
