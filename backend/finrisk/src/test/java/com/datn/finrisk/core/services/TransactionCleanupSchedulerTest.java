package com.datn.finrisk.core.services;

import com.datn.finrisk.core.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionCleanupScheduler — Unit Tests")
class TransactionCleanupSchedulerTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AuditLogService       auditLogService;

    @InjectMocks
    private TransactionCleanupScheduler scheduler;

    // ── expireStaleUnderReviewTransactions ────────────────────────────────────

    @Nested
    @DisplayName("expireStaleUnderReviewTransactions()")
    class ExpireUnderReview {

        @Test
        @DisplayName("no stale IDs → bulk update and audit NOT called")
        void noStaleIds_noUpdate() {
            when(transactionRepository.findStaleUnderReviewIds(any())).thenReturn(Collections.emptyList());

            scheduler.expireStaleUnderReviewTransactions();

            verify(transactionRepository, never()).expireStaleUnderReview(any());
            verify(auditLogService, never()).logAction(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("2 stale IDs → bulk update called once + 2 audit log entries")
        void twoStaleIds_bulkUpdateAndTwoAuditLogs() {
            List<Long> ids = Arrays.asList(11L, 22L);
            when(transactionRepository.findStaleUnderReviewIds(any())).thenReturn(ids);
            when(transactionRepository.expireStaleUnderReview(any())).thenReturn(2);

            scheduler.expireStaleUnderReviewTransactions();

            verify(transactionRepository).expireStaleUnderReview(any());
            verify(auditLogService, times(2)).logAction(eq("SYSTEM"), eq("REVIEW_TIMEOUT"), anyString());
        }

        @Test
        @DisplayName("audit message contains transaction ID")
        void auditMessage_containsTxId() {
            when(transactionRepository.findStaleUnderReviewIds(any())).thenReturn(List.of(77L));
            when(transactionRepository.expireStaleUnderReview(any())).thenReturn(1);

            scheduler.expireStaleUnderReviewTransactions();

            verify(auditLogService).logAction(eq("SYSTEM"), eq("REVIEW_TIMEOUT"),
                    contains("77"));
        }
    }

    // ── cleanupStalledTransactions ────────────────────────────────────────────

    @Nested
    @DisplayName("cleanupStalledTransactions()")
    class CleanupStalled {

        @Test
        @DisplayName("no stalled IDs → no update and no audit")
        void noStalledIds_noAction() {
            when(transactionRepository.findStalledTransactionIds(any())).thenReturn(Collections.emptyList());

            scheduler.cleanupStalledTransactions();

            verify(transactionRepository, never()).expireStaleByIds(anyList());
            verify(auditLogService, never()).logAction(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("3 stalled IDs → bulk expire called + 3 TX_EXPIRED audit entries")
        void threeStalledIds_expiresAndAudits() {
            List<Long> ids = Arrays.asList(1L, 2L, 3L);
            when(transactionRepository.findStalledTransactionIds(any())).thenReturn(ids);
            when(transactionRepository.expireStaleByIds(ids)).thenReturn(3);

            scheduler.cleanupStalledTransactions();

            verify(transactionRepository).expireStaleByIds(ids);
            verify(auditLogService, times(3)).logAction(eq("SYSTEM"), eq("TX_EXPIRED"), anyString());
        }

        @Test
        @DisplayName("TX_EXPIRED audit message contains transaction ID")
        void auditMessage_containsTxId() {
            when(transactionRepository.findStalledTransactionIds(any())).thenReturn(List.of(42L));
            when(transactionRepository.expireStaleByIds(any())).thenReturn(1);

            scheduler.cleanupStalledTransactions();

            verify(auditLogService).logAction(eq("SYSTEM"), eq("TX_EXPIRED"), contains("42"));
        }

        @Test
        @DisplayName("expire called with the exact stalled ID list")
        void expireCalledWithExactList() {
            List<Long> ids = Arrays.asList(100L, 200L);
            when(transactionRepository.findStalledTransactionIds(any())).thenReturn(ids);
            when(transactionRepository.expireStaleByIds(ids)).thenReturn(2);

            scheduler.cleanupStalledTransactions();

            verify(transactionRepository).expireStaleByIds(ids);
        }
    }
}
