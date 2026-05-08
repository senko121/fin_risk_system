
package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.RiskPolicy;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.TransactionLedger;
import com.datn.finrisk.core.entities.UserSecurity;
import com.datn.finrisk.core.exceptions.BusinessLogicException;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.RiskPolicyRepository;
import com.datn.finrisk.core.repository.TransactionLedgerRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.repository.UserSecurityRepository;
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
    @Autowired private UserSecurityRepository userSecurityRepository;

    //   BÍ QUYẾT LÀ ĐÂY: Spring tự gom cả 3 class Strategy vào cái Map này!
    @Autowired
    private Map<String, RiskActionStrategy> actionStrategies; 

    @Transactional
    public Transaction executeAfterOtp(Transaction tx) {
        RiskActionStrategy strategy = actionStrategies.get("passActionStrategy");
        return strategy.execute(tx);
    }

    private boolean checkIsNewRecipient(Long accountId, String toAccountNumber) {
        // 🚀 1 query thay vì load toàn bộ lịch sử
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
            throw new BusinessLogicException("ERR_PIN_LOCKED", "Tài khoản đang bị tạm khóa giao dịch do nhập sai PIN nhiều lần. Vui lòng thử lại sau!");
        }

        // Các logic validate cơ bản
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền giao dịch phải lớn hơn 0 hợp lệ!");
        }

        if (senderAccount.getAccountNumber().equals(toAccountNumber)) {
            throw new IllegalArgumentException("Phát hiện gian lận: Không thể tự chuyển tiền cho chính mình!");
        }

        if (senderAccount.getBalance().compareTo(amount) < 0) {
            throw new BusinessLogicException("ERR_INSUFFICIENT_BALANCE", "Số dư không đủ để thực hiện giao dịch!");
        }

        // Tạo giao dịch mới
        Transaction tx = new Transaction();
        tx.setFromAccount(senderAccount);
        tx.setToAccountNumber(toAccountNumber);
        tx.setAmount(amount);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setDescription(description); 
        
        // Đổ dữ liệu IP và Device vào Transaction trước khi lưu
        tx.setLocationIp(ip);
        tx.setDeviceFingerprint(device);

        boolean isNewRecipient = checkIsNewRecipient(senderAccount.getId(), toAccountNumber);
        
        // 1. Tính tổng điểm rủi ro
        int riskScore = riskEvaluationService.evaluateRisk(tx, isNewRecipient);
        tx.setTotalRiskScore(riskScore);

        // 2. Tra cứu Policy từ Database
        RiskPolicy policy = riskPolicyRepo.findByScore(riskScore)
                .orElseThrow(() -> new BusinessLogicException("ERR_POLICY_NOT_FOUND", "LỖI HỆ THỐNG: Không tìm thấy Policy xử lý cho mức điểm " + riskScore));

        System.out.println("🔎 Tra cứu Database: Điểm " + riskScore + " rơi vào Policy [" + policy.getDescription() + "]");

        // 3. Lấy tên Chiến thuật ra và gọi lệnh chạy!
        RiskActionStrategy strategy = actionStrategies.get(policy.getActionBeanName());
        
        if (strategy == null) {
            throw new BusinessLogicException("ERR_STRATEGY_NOT_FOUND", "LỖI CODE: Không tìm thấy class xử lý cho hành động " + policy.getActionBeanName());
        }

        return strategy.execute(tx);
    }
    
    @Transactional(rollbackFor = Exception.class)
    public Transaction executeTransactionCore(Transaction tx) {
        System.out.println("✅ XÁC THỰC THÀNH CÔNG -> ĐÓNG MỘC TRỪ TIỀN VÀO SỔ CÁI");
        
        tx.setStatus("SUCCESS");
        Transaction savedTx = transactionRepository.save(tx);

        // Trừ tiền người gửi
        Account sender = savedTx.getFromAccount();
        sender.setBalance(sender.getBalance().subtract(savedTx.getAmount()));
        accountRepository.save(sender);

        TransactionLedger debit = new TransactionLedger();
        debit.setTransaction(savedTx);
        debit.setAccount(sender);
        debit.setEntryType("DEBIT");
        debit.setAmount(savedTx.getAmount());
        debit.setBalanceAfter(sender.getBalance());
        transactionLedgerRepository.save(debit);

        // Cộng tiền người nhận
        accountRepository.findByAccountNumber(savedTx.getToAccountNumber()).ifPresent(receiver -> {
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

        return savedTx;
    }
}