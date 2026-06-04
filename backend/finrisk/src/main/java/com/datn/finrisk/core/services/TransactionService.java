 
package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.RiskPolicy;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.TransactionLedger;
import com.datn.finrisk.core.entities.UserSecurity;
import com.datn.finrisk.core.exceptions.BusinessLogicException;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.RiskPolicyRepository;
import com.datn.finrisk.core.repository.RiskScoreRepository;
import com.datn.finrisk.core.repository.TransactionLedgerRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.repository.UserSecurityRepository;
import com.datn.finrisk.core.strategies.RiskActionStrategy;
import com.datn.finrisk.core.entities.TransactionAiInsight;
import com.datn.finrisk.core.repository.TransactionAiInsightRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.datn.finrisk.core.entities.RiskScore;

import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

//Transaction B9: đóng vai trò tonggor chỉ huy gói toan bọ quá trinh trên initiateTransaction -> Transaction B10: Auditllogserrvice
//Transaction B9 Phase 2: bắt đau gọi hafm xử lý tiền bạcs executeTransactionCore qua trính thực hiện UPDATE  cacs ảng account va INSERT  vao transactiion ledgers
//Transaction B10 Phase 2 : AuditLogService
@Slf4j
@Service
public class TransactionService {

    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionLedgerRepository transactionLedgerRepository;
    @Autowired private RiskEvaluationService riskEvaluationService;
    @Autowired private RiskPolicyRepository riskPolicyRepo;
    @Autowired private UserSecurityRepository userSecurityRepository;
    @Autowired private BehaviorLearningService behaviorLearningService;
    
 
    @Autowired private TransactionAiInsightRepository aiInsightRepository;

 
    @Autowired
    private Map<String, RiskActionStrategy> actionStrategies; 

    @Autowired private RiskScoreRepository riskScoreRepository;

 
    private static final Map<String, Integer> OVERRIDE_PRIORITY = Map.of(
        "LOW", 0, 
        "MEDIUM_1", 1, 
        "MEDIUM_2", 2, 
        "HIGH", 3
    );

    private boolean checkIsNewRecipient(Long accountId, String toAccountNumber) {
 
        return !transactionLedgerRepository
            .existsByAccountIdAndToAccountNumber(accountId, toAccountNumber);
    }


    @Transactional(rollbackFor = Exception.class)
    public Transaction initiateTransaction(Long fromAccountId, String toAccountNumber, BigDecimal amount, String description, String ip, String device) {

 
        Account senderAccount = accountRepository.findByIdWithUserAndSecurity(fromAccountId)
                .orElseThrow(() -> new BusinessLogicException("ERR_NOT_FOUND", "Tài khoản không tồn tại!"));

        UserSecurity security = senderAccount.getUser().getUserSecurity();

 
        if (security.getPinHash() == null || security.getPinHash().trim().isEmpty()) {
            throw new BusinessLogicException("ERR_NO_PIN_SETUP", "Tài khoản chưa thiết lập mã Smart PIN. Vui lòng thiết lập để giao dịch!");
        }

        if (security.getLockUntil() != null && security.getLockUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessLogicException("ERR_PIN_LOCKED", "Tài khoản đang bị tạm khóa giao dịch. Vui lòng thử lại sau!");
        }
 
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền giao dịch không hợp lệ!");
        }

        if (senderAccount.getAccountNumber().equals(toAccountNumber)) {
            throw new IllegalArgumentException("Không thể tự chuyển tiền cho chính mình!");
        }

        if (senderAccount.getBalance().compareTo(amount) < 0) {
            throw new BusinessLogicException("ERR_INSUFFICIENT_BALANCE", "Số dư không đủ để thực hiện giao dịch!");
        }
 
        Transaction tx = new Transaction();
        tx.setFromAccount(senderAccount);
        tx.setToAccountNumber(toAccountNumber);
        tx.setAmount(amount);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setDescription(description); 
        tx.setLocationIp(ip);
        tx.setDeviceFingerprint(device);
        tx.setStatus("PENDING");
 
        boolean isNewRecipient = checkIsNewRecipient(senderAccount.getId(), toAccountNumber);
 
        List<RiskScore> pendingRiskLogs = new ArrayList<>();
        List<TransactionAiInsight> pendingAiInsights = new ArrayList<>();  
 
        int riskScore = riskEvaluationService.evaluateRisk(tx, isNewRecipient, pendingRiskLogs, pendingAiInsights);
        tx.setTotalRiskScore(riskScore);
 
        tx = transactionRepository.save(tx);
        log.debug("Transaction saved: id={}", tx.getId());

        if (!pendingRiskLogs.isEmpty()) {
            final Transaction savedTx = tx;
            pendingRiskLogs.forEach(r -> r.setTransaction(savedTx));
            riskScoreRepository.saveAll(pendingRiskLogs);
            log.debug("Saved {} risk score(s) for tx={}", pendingRiskLogs.size(), savedTx.getId());
        }

        if (!pendingAiInsights.isEmpty()) {
            final Transaction savedTx = tx;
            pendingAiInsights.forEach(i -> i.setTransaction(savedTx));
            aiInsightRepository.saveAll(pendingAiInsights);
            log.debug("Saved {} AI insight(s) for tx={}", pendingAiInsights.size(), savedTx.getId());
        }

 
        RiskPolicy policy = riskPolicyRepo.findByScore(riskScore)
                .orElseThrow(() -> new BusinessLogicException("ERR_POLICY_NOT_FOUND", "Không tìm thấy Policy xử lý cho mức điểm " + riskScore));
 
        String policyOverride = tx.getPolicyOverride();
        if (policyOverride != null) {
            RiskPolicy overridePolicy = riskPolicyRepo.findByRiskLevel(policyOverride).orElse(null);

            if (overridePolicy != null) {
                boolean overrideIsStricter =
                    OVERRIDE_PRIORITY.getOrDefault(overridePolicy.getRiskLevel(), 0) >
                    OVERRIDE_PRIORITY.getOrDefault(policy.getRiskLevel(), 0);

                if (overrideIsStricter) {
                    log.info("Policy override applied: {} → {} (business rule)", policy.getRiskLevel(), overridePolicy.getRiskLevel());
                    policy = overridePolicy;
                }
            }
        }

        tx.setRiskLevel(policy.getRiskLevel());  
        log.info("Risk evaluation: score={} level={} desc={}", riskScore, policy.getRiskLevel(), policy.getDescription());
 
        RiskActionStrategy strategy = actionStrategies.get(policy.getActionBeanName());
        
        if (strategy == null) {
            throw new BusinessLogicException("ERR_STRATEGY_NOT_FOUND", "Không tìm thấy class xử lý cho hành động " + policy.getActionBeanName());
        }
 
        return strategy.execute(tx);
    }
    
    @Transactional(rollbackFor = Exception.class)
    public Transaction executeTransactionCore(Transaction tx) {
        // 1. Atomic claim — exactly one thread/request wins; duplicates are rejected at DB level
        int claimed = transactionRepository.claimForExecution(tx.getId());
        if (claimed == 0) {
            log.warn("[TxCore] tx={} already claimed or not in a pending state — aborting duplicate execution.", tx.getId());
            throw new BusinessLogicException("ERR_DUPLICATE_EXECUTION",
                "Giao dịch đang được xử lý hoặc đã hoàn tất.");
        }
        log.info("Transaction execution claimed: id={}", tx.getId());

        tx.setStatus("SUCCESS");
        Transaction savedTx = transactionRepository.save(tx);

        boolean isNewRecipient = checkIsNewRecipient(savedTx.getFromAccount().getId(), savedTx.getToAccountNumber());

        // 2. Re-load sender with a pessimistic write lock — guarantees fresh balance, prevents lost updates
        Account sender = accountRepository.findByIdForUpdate(savedTx.getFromAccount().getId())
            .orElseThrow(() -> new BusinessLogicException("ERR_NOT_FOUND", "Tài khoản nguồn không tồn tại!"));

        // 3. Re-check balance at execution time (may have changed since initiation)
        if (sender.getBalance().compareTo(savedTx.getAmount()) < 0) {
            throw new BusinessLogicException("ERR_INSUFFICIENT_BALANCE",
                "Số dư không đủ tại thời điểm thực hiện giao dịch!");
        }

        sender.setBalance(sender.getBalance().subtract(savedTx.getAmount()));
        accountRepository.save(sender);

        TransactionLedger debit = new TransactionLedger();
        debit.setTransaction(savedTx);
        debit.setAccount(sender);
        debit.setEntryType("DEBIT");
        debit.setAmount(savedTx.getAmount());
        debit.setBalanceAfter(sender.getBalance());
        transactionLedgerRepository.save(debit);

        // 4. Lock receiver account before crediting to prevent concurrent balance corruption
        accountRepository.findByAccountNumberForUpdate(savedTx.getToAccountNumber()).ifPresent(receiver -> {
            receiver.setBalance(receiver.getBalance().add(savedTx.getAmount()));
            accountRepository.save(receiver);

            TransactionLedger credit = new TransactionLedger();
            credit.setTransaction(savedTx);
            credit.setAccount(receiver);
            credit.setEntryType("CREDIT");
            credit.setAmount(savedTx.getAmount());
            credit.setBalanceAfter(receiver.getBalance());
            transactionLedgerRepository.save(credit);
        });

        behaviorLearningService.learnFromTransaction(savedTx, isNewRecipient);

        return savedTx;
    }
 
    @Transactional(rollbackFor = Exception.class)
    public Transaction executeReversalCore(Transaction tx) {
        // 1. Atomic claim — only one request can reverse a SUCCESS transaction
        int claimed = transactionRepository.claimForReversal(tx.getId());
        if (claimed == 0) {
            log.warn("[ReversalCore] tx={} already claimed for reversal or not SUCCESS — aborting.", tx.getId());
            throw new BusinessLogicException("ERR_DUPLICATE_REVERSAL",
                "Giao dịch đã được hoàn tác hoặc không ở trạng thái hợp lệ để hoàn tác.");
        }

        tx.setStatus("REVERSED");
        Transaction reversedTx = transactionRepository.save(tx);

        BigDecimal reversalAmount = reversedTx.getAmount();

        // 2. Re-load sender with a pessimistic write lock — guarantees fresh balance
        Account originalSender = accountRepository.findByIdForUpdate(reversedTx.getFromAccount().getId())
            .orElseThrow(() -> new BusinessLogicException("ERR_NOT_FOUND", "Tài khoản nguồn không tồn tại!"));

        originalSender.setBalance(originalSender.getBalance().add(reversalAmount));
        accountRepository.save(originalSender);

        TransactionLedger refundCredit = new TransactionLedger();
        refundCredit.setTransaction(reversedTx);
        refundCredit.setAccount(originalSender);
        refundCredit.setEntryType("CREDIT");
        refundCredit.setAmount(reversalAmount);
        refundCredit.setBalanceAfter(originalSender.getBalance());
        transactionLedgerRepository.save(refundCredit);

        // 3. Lock receiver account before clawback
        Optional<Account> receiverOpt = accountRepository.findByAccountNumberForUpdate(reversedTx.getToAccountNumber());
        if (receiverOpt.isPresent()) {
            Account originalReceiver = receiverOpt.get();

            // 4. Floor check — receiver may have spent the credited funds; refuse rather than produce negative balance
            if (originalReceiver.getBalance().compareTo(reversalAmount) < 0) {
                throw new BusinessLogicException("ERR_REVERSAL_INSUFFICIENT_FUNDS",
                    "Số dư tài khoản nhận không đủ để thu hồi. Cần can thiệp thủ công.");
            }

            originalReceiver.setBalance(originalReceiver.getBalance().subtract(reversalAmount));
            accountRepository.save(originalReceiver);

            TransactionLedger clawbackDebit = new TransactionLedger();
            clawbackDebit.setTransaction(reversedTx);
            clawbackDebit.setAccount(originalReceiver);
            clawbackDebit.setEntryType("DEBIT");
            clawbackDebit.setAmount(reversalAmount);
            clawbackDebit.setBalanceAfter(originalReceiver.getBalance());
            transactionLedgerRepository.save(clawbackDebit);
        }

        return reversedTx;
    }
}