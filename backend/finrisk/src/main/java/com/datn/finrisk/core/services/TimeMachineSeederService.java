package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.*;
import com.datn.finrisk.core.repository.*;
import com.datn.finrisk.core.utils.MockDataGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class TimeMachineSeederService {

    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionLedgerRepository ledgerRepository;
    @Autowired private BehaviorLearningService behaviorLearningService;
    @Autowired private MockDataGenerator mockGen;

    private final Set<String> studentRecipients = new HashSet<>();
    private final Set<String> workerRecipients  = new HashSet<>();
    private final Set<String> vipRecipients     = new HashSet<>();
    private final Map<Long, LocalDateTime> lastTxTimeCache = new HashMap<>();

    @Transactional
    public String startTimeTravelSimulation() {
        System.out.println("⏳ [TIME MACHINE] Bắt đầu giả lập 180 ngày...");

        lastTxTimeCache.clear();
        studentRecipients.clear();
        workerRecipients.clear();
        vipRecipients.clear();

        Account godAcc = accountRepository.findByAccountNumberWithUser("9999999991")
            .orElseThrow(() -> new RuntimeException("Thiếu tài khoản Chúa '9999999991'"));
        Account svAcc  = accountRepository.findByAccountNumberWithUser("SV_MAIN_001")
            .orElseThrow(() -> new RuntimeException("Thiếu SV_MAIN_001"));
        Account cnAcc  = accountRepository.findByAccountNumberWithUser("CN_MAIN_001")
            .orElseThrow(() -> new RuntimeException("Thiếu CN_MAIN_001"));
        Account vipAcc = accountRepository.findByAccountNumberWithUser("VIP_MAIN_001")
            .orElseThrow(() -> new RuntimeException("Thiếu VIP_MAIN_001"));

        // Reset số dư đủ để chạy 180 ngày
        svAcc.setBalance(BigDecimal.valueOf(50_000_000.0));
        cnAcc.setBalance(BigDecimal.valueOf(50_000_000.0));
        vipAcc.setBalance(BigDecimal.valueOf(500_000_000.0));
        accountRepository.save(svAcc);
        accountRepository.save(cnAcc);
        accountRepository.save(vipAcc);

        LocalDateTime endTime  = LocalDateTime.now();
        LocalDateTime timeline = endTime.minusDays(180);
        int totalTx = 0;
        int skipped = 0;

        while (timeline.isBefore(endTime)) {
            int dom = timeline.getDayOfMonth();

            // ══ NẠP TIỀN TRƯỚC (đầu ngày) ══════════════
            if (dom == 1)
                executeMockIncome(godAcc, svAcc, 4_000_000.0,
                    timeline.withHour(7).withMinute(0));
            if (dom == 15 || dom == 30)
                executeMockIncome(godAcc, svAcc, 1_500_000.0,
                    timeline.withHour(7).withMinute(30));
            if (dom == 10)
                executeMockIncome(godAcc, cnAcc, 8_500_000.0,
                    timeline.withHour(7).withMinute(0));
            int incomeCount = mockGen.nextInt(2, 4);
            for (int in = 0; in < incomeCount; in++) {
                executeMockIncome(godAcc, vipAcc,
                    mockGen.nextInt(20_000_000, 80_000_000),
                    timeline.withHour(mockGen.nextInt(7, 10)));
            }

            // ══ CHI TIÊU SAU ════════════════════════════

            // Sinh viên
            if (dom <= 24) {
                int count = mockGen.nextInt(2, 4);
                for (int t = 0; t < count; t++) {
                    double hour = mockGen.nextInt(1, 10) > 7
                        ? mockGen.generateGaussianHour(22.0, 1.5)
                        : mockGen.generateGaussianHour(12.0, 1.0);
                    double amount = mockGen.nextInt(1, 10) > 8
                        ? mockGen.nextInt(150_000, 400_000)
                        : mockGen.nextInt(15_000, 70_000);
                    String toAcc  = mockGen.getRandomSinkAccount(20);
                    boolean isNew = studentRecipients.add(toAcc);
                    if (executeMockTransaction(svAcc, toAcc, amount, safeTime(timeline, hour), isNew)) totalTx++;
                    else skipped++;
                }
            } else {
                if (mockGen.nextInt(1, 10) > 7) {
                    double hour = mockGen.generateGaussianHour(12.0, 2.0);
                    if (executeMockTransaction(svAcc, "SINK_ACC_001",
                            mockGen.nextInt(20_000, 40_000), safeTime(timeline, hour), false)) totalTx++;
                    else skipped++;
                }
            }

            // Công nhân
            if (dom == 11 && cnAcc.getBalance().doubleValue() >= 6_000_000) {
                if (executeMockTransaction(cnAcc, "SINK_ACC_099", 6_000_000.0,
                        timeline.withHour(19).withMinute(15), false)) totalTx++;
                else skipped++;
            }
            if (dom != 10 && dom != 11) {
                for (int targetHour : new int[]{6, 12, 18}) {
                    if (mockGen.nextInt(1, 10) > 4) {
                        double hour  = mockGen.generateGaussianHour(targetHour + 0.3, 0.2);
                        String toAcc = targetHour == 6  ? "SINK_ACC_021"
                                     : targetHour == 12 ? "SINK_ACC_022" : "SINK_ACC_023";
                        if (executeMockTransaction(cnAcc, toAcc,
                                mockGen.nextInt(30_000, 70_000), safeTime(timeline, hour), false)) totalTx++;
                        else skipped++;
                    }
                }
            }

            // Đại gia
            int vipCount = mockGen.nextInt(3, 5);
            for (int v = 0; v < vipCount; v++) {
                double hour = mockGen.nextInt(1, 10) <= 3
                    ? mockGen.generateGaussianHour(1.5, 1.0)
                    : mockGen.generateGaussianHour(15.0, 4.0);
                double amount = mockGen.nextInt(1, 10) > 7
                    ? mockGen.nextInt(50_000_000, 200_000_000)
                    : mockGen.nextInt(100_000, 5_000_000);
                String toAcc  = mockGen.getRandomSinkAccount(100);
                boolean isNew = vipRecipients.add(toAcc);
                if (executeMockTransaction(vipAcc, toAcc, amount, safeTime(timeline, hour), isNew)) totalTx++;
                else skipped++;
            }

            timeline = timeline.plusDays(1);
        }

        String summary = String.format(
            "✅ Hoàn thành! GD học được: %d | Bỏ qua (thiếu tiền): %d", totalTx, skipped);
        System.out.println("🏁 [TIME MACHINE] " + summary);
        return summary;
    }

    // Trả về true nếu thành công, false nếu bỏ qua do thiếu tiền
    private boolean executeMockTransaction(
            Account fromAcc, String toAccNum,
            double amount, LocalDateTime txTime,
            boolean isNewRecipient) {

        double currentBalance = fromAcc.getBalance().doubleValue();
        if (currentBalance - amount < 0) {
            System.out.printf("⚠️ [SKIP] %s: %.0f < %.0f%n",
                fromAcc.getAccountNumber(), currentBalance, amount);
            return false;
        }

        Transaction tx = new Transaction();
        tx.setFromAccount(fromAcc);
        tx.setToAccountNumber(toAccNum);
        tx.setToBankCode("INTERNAL");
        tx.setAmount(BigDecimal.valueOf(amount));
        tx.setStatus("SUCCESS");
        tx.setCreatedAt(txTime);
        tx.setRiskLevel("LOW");
        tx = transactionRepository.save(tx);

        double newBalance = currentBalance - amount;
        fromAcc.setBalance(BigDecimal.valueOf(newBalance));
        accountRepository.save(fromAcc);

        TransactionLedger ledger = new TransactionLedger();
        ledger.setTransaction(tx);
        ledger.setAccount(fromAcc);
        ledger.setEntryType("DEBIT");
        ledger.setAmount(BigDecimal.valueOf(amount));
        ledger.setBalanceAfter(BigDecimal.valueOf(newBalance));
        ledger.setCreatedAt(txTime);
        ledgerRepository.save(ledger);

        Long userId = fromAcc.getUser().getId();
        LocalDateTime lastTime = lastTxTimeCache.get(userId);
        double gapSeconds = lastTime != null
            ? Math.max(Duration.between(lastTime, txTime).getSeconds(), 1.0)
            : 86_400.0;
        lastTxTimeCache.put(userId, txTime);

        behaviorLearningService.learnFromTransactionSync(tx, isNewRecipient, gapSeconds);
        return true;
    }

    private void executeMockIncome(
            Account godAcc, Account toAcc,
            double amount, LocalDateTime txTime) {

        Transaction tx = new Transaction();
        tx.setFromAccount(godAcc);
        tx.setToAccountNumber(toAcc.getAccountNumber());
        tx.setToBankCode("INTERNAL");
        tx.setAmount(BigDecimal.valueOf(amount));
        tx.setStatus("SUCCESS");
        tx.setCreatedAt(txTime);
        tx.setRiskLevel("LOW");
        transactionRepository.save(tx);

        double newBalance = toAcc.getBalance().doubleValue() + amount;
        toAcc.setBalance(BigDecimal.valueOf(newBalance));
        accountRepository.save(toAcc);

        TransactionLedger ledger = new TransactionLedger();
        ledger.setTransaction(tx);
        ledger.setAccount(toAcc);
        ledger.setEntryType("CREDIT");
        ledger.setAmount(BigDecimal.valueOf(amount));
        ledger.setBalanceAfter(BigDecimal.valueOf(newBalance));
        ledger.setCreatedAt(txTime);
        ledgerRepository.save(ledger);
    }

    private LocalDateTime safeTime(LocalDateTime base, double hour) {
        int h = Math.max(0, Math.min(23, (int) hour));
        int m = Math.max(0, Math.min(59, (int) ((hour - (int) hour) * 60)));
        return base.withHour(h).withMinute(m).withSecond(0);
    }
}