package com.datn.finrisk.core.services;

import com.datn.finrisk.core.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class TransactionCleanupScheduler {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AuditLogService auditLogService;
 
    // Runs hourly — no need for a 5-minute interval when the threshold is 24 hours.
    @Scheduled(fixedDelay = 3_600_000)
    public void expireStaleUnderReviewTransactions() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);

        // Step 1: collect IDs before the update so each can be individually audited.
        // Uses a projection query (id only) — no entity hydration, no JOIN cascade.
        List<Long> staleIds = transactionRepository.findStaleUnderReviewIds(threshold);
        if (staleIds.isEmpty()) return;

        log.info("[SCHEDULER][REVIEW-TIMEOUT] Found {} UNDER_REVIEW transactions older than 24h", staleIds.size());

        // Step 2: single atomic bulk UPDATE. WHERE status='UNDER_REVIEW' guarantees that
        // any row an admin already processed (PROCESSING / BLOCKED) is silently skipped.
        int expired = transactionRepository.expireStaleUnderReview(threshold);

        // Step 3: per-transaction audit entries.
        // staleIds may over-count by ≤ the number of rows an admin processed in the
        // microseconds between the SELECT and the UPDATE — acceptable in a fraud context.
        for (Long txId : staleIds) {
            auditLogService.logAction("SYSTEM", "REVIEW_TIMEOUT",
                "Giao dịch #" + txId + " tự động hết hạn sau 24h không được kiểm duyệt. Không có tiền bị chuyển.");
        }

        log.info("[SCHEDULER][REVIEW-TIMEOUT] Marked EXPIRED: {}/{} UNDER_REVIEW transactions", expired, staleIds.size());
    }

    @Scheduled(fixedDelay = 300000)
    public void cleanupStalledTransactions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(20);

        // Step 1: ID-only projection — no entity hydration, no JOIN cascade.
        List<Long> stalledIds = transactionRepository.findStalledTransactionIds(threshold);
        if (stalledIds.isEmpty()) return;

        log.info("[SCHEDULER][STALLED] Found {} stalled transactions older than 20min", stalledIds.size());

        // Step 2: single atomic bulk UPDATE. WHERE status IN (...) guard ensures that any row
        // already transitioned to PROCESSING or SUCCESS by a concurrent execution is skipped
        // silently — prevents CF1 overwrite of a committed SUCCESS with EXPIRED.
        int expired = transactionRepository.expireStaleByIds(stalledIds);

        // Step 3: per-transaction audit entries.
        // stalledIds may over-count by ≤ the number of rows that raced to execution between
        // the SELECT and the UPDATE — acceptable; the audit trail records intent, not outcome.
        for (Long txId : stalledIds) {
            auditLogService.logAction("SYSTEM", "TX_EXPIRED",
                "Giao dịch #" + txId + " hết hạn do mất kết nối.");
            log.info("[SCHEDULER][STALLED] Expired stalled transaction txId={}", txId);
        }

        log.info("[SCHEDULER][STALLED] Marked EXPIRED: {}/{} stalled transactions", expired, stalledIds.size());
    }
}