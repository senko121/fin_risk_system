
package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.RiskPolicy;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.TransactionLedger;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.RiskPolicyRepository;
import com.datn.finrisk.core.repository.TransactionLedgerRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.strategies.RiskActionStrategy; // Dòng ma thuật đây!
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class TransactionService {

    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionLedgerRepository transactionLedgerRepository;
    @Autowired private RiskEvaluationService riskEvaluationService;
    @Autowired private RiskPolicyRepository riskPolicyRepo;

    //   BÍ QUYẾT LÀ ĐÂY: Spring tự gom cả 3 class Strategy vào cái Map này!
    @Autowired
    private Map<String, RiskActionStrategy> actionStrategies; 

    @Transactional
    public Transaction executeAfterOtp(Transaction tx) {
        RiskActionStrategy strategy = actionStrategies.get("passActionStrategy");
        return strategy.execute(tx);
    }

    private boolean checkIsNewRecipient(Long accountId, String toAccountNumber) {
        List<TransactionLedger> ledgers = transactionLedgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        return ledgers.stream()
                .filter(l -> l.getEntryType().equals("DEBIT"))
                .noneMatch(l -> l.getTransaction().getToAccountNumber().equals(toAccountNumber));
    }

@Transactional(rollbackFor = Exception.class)
    // 🚀 BƯỚC 1: Thêm 'String description' vào tham số
    public Transaction initiateTransaction(Long fromAccountId, String toAccountNumber, BigDecimal amount, String description) {

        Account senderAccount = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại!"));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền giao dịch phải lớn hơn 0 hợp lệ!");
        }

        if (senderAccount.getAccountNumber().equals(toAccountNumber)) {
            throw new IllegalArgumentException("Phát hiện gian lận: Không thể tự chuyển tiền cho chính mình!");
        }

        if (senderAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Số dư không đủ!");
        }

        Transaction tx = new Transaction();
        tx.setFromAccount(senderAccount);
        tx.setToAccountNumber(toAccountNumber);
        tx.setAmount(amount);
        tx.setCreatedAt(LocalDateTime.now());
        
        // 🚀 BƯỚC 2: Set cái description vào Transaction
        tx.setDescription(description); 

        boolean isNewRecipient = checkIsNewRecipient(senderAccount.getId(), toAccountNumber);
        
        // 1. Tính tổng điểm rủi ro
        int riskScore = riskEvaluationService.evaluateRisk(tx, isNewRecipient);
        tx.setTotalRiskScore(riskScore);

        // 2. Tra cứu Policy từ Database
        RiskPolicy policy = riskPolicyRepo.findByScore(riskScore)
                .orElseThrow(() -> new RuntimeException("LỖI HỆ THỐNG: Không tìm thấy Policy xử lý cho mức điểm " + riskScore));

        System.out.println("🔎 Tra cứu Database: Điểm " + riskScore + " rơi vào Policy [" + policy.getDescription() + "]");

        // 3. Lấy tên Chiến thuật ra và gọi lệnh chạy!
        RiskActionStrategy strategy = actionStrategies.get(policy.getActionBeanName());
        
        if (strategy == null) {
            throw new RuntimeException("LỖI CODE: Không tìm thấy class xử lý cho hành động " + policy.getActionBeanName());
        }

        return strategy.execute(tx);
    }
}