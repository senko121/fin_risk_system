package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TransactionCleanupScheduler {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AuditLogService auditLogService;
 
    @Scheduled(fixedDelay = 300000)
    public void cleanupStalledTransactions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<Transaction> stalled = transactionRepository.findStalledTransactions(threshold);

        if (stalled.isEmpty()) return;

        System.out.println("🧹 [SCHEDULER] Tìm thấy " + stalled.size() + " giao dịch bị treo...");

        for (Transaction tx : stalled) {
            tx.setStatus("EXPIRED");
            transactionRepository.save(tx);
            
            String username = tx.getFromAccount() != null && tx.getFromAccount().getUser() != null
                ? tx.getFromAccount().getUser().getUsername()
                : "unknown";
            
            auditLogService.logAction(username, "TX_EXPIRED", 
                "Giao dịch #" + tx.getId() + " hết hạn do mất kết nối.");
            
            System.out.println("⏰ [SCHEDULER] Đã hủy giao dịch treo: #" + tx.getId());
        }
    }
}