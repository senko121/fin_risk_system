package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransactionService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RiskEvaluationService riskEvaluationService; // Gọi thằng Rule Engine ở trên vào

    @Transactional(rollbackFor = Exception.class)
    public Transaction processTransaction(Long fromAccountId, String toAccountNumber, BigDecimal amount, String emotionSignal) {
        
        // 1. Kiểm tra tài khoản gửi có tồn tại không
        Account senderAccount = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản gửi!"));

        // 2. Kiểm tra số dư (Core Banking Logic)
        if (senderAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Số dư không đủ để thực hiện giao dịch!");
        }

        // 3. Khởi tạo Giao dịch (Tạm thời là PENDING)
        Transaction tx = new Transaction();
        tx.setFromAccount(senderAccount);
        tx.setToAccountNumber(toAccountNumber);
        tx.setAmount(amount);
        tx.setEmotionSignal(emotionSignal);
        tx.setStatus("PENDING");

        // 4. CHẠY RULE ENGINE ĐÁNH GIÁ RỦI RO [cite: 221]
        int riskScore = riskEvaluationService.evaluateRisk(tx);
        tx.setTotalRiskScore(riskScore);

        // 5. Phân loại Rủi ro [cite: 81-83, 222-225]
        if (riskScore <= 30) {
            tx.setRiskLevel("LOW");
            // Rủi ro thấp -> Trừ tiền luôn (Thực thi ACID)
            senderAccount.setBalance(senderAccount.getBalance().subtract(amount));
            tx.setStatus("SUCCESS");
        } else if (riskScore <= 70) {
            tx.setRiskLevel("MEDIUM");
            // Rủi ro trung bình -> Treo đó, yêu cầu OTP
            tx.setStatus("REQUIRES_OTP"); 
        } else {
            tx.setRiskLevel("HIGH");
            // Rủi ro cao -> Treo đó, yêu cầu FaceID
            tx.setStatus("REQUIRES_FACE_SCAN");
        }

        // Lưu vào Database
        accountRepository.save(senderAccount);
        return transactionRepository.save(tx);
    }
}