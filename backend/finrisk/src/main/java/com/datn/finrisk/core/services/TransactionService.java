package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.TransactionLedger;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.TransactionLedgerRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class TransactionService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RiskEvaluationService riskEvaluationService;

    @Autowired
    private TransactionLedgerRepository transactionLedgerRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private EmailService emailService;

    @Value("${twilio.account.sid}")
    private String twilioAccountSid;

    @Value("${twilio.auth.token}")
    private String twilioAuthToken;

    @Value("${twilio.phone.number}")
    private String twilioPhoneNumber;

    @Value("${app.test.phone.number}")
    private String userPhoneNumber;

    @PostConstruct
    public void initTwilio() {
        Twilio.init(twilioAccountSid, twilioAuthToken);
    }

    private String generateOTP() {
        return String.format("%06d", new Random().nextInt(999999));
    }

    // Hàm kiểm tra xem đã từng chuyển tiền cho số này chưa
    private boolean checkIsNewRecipient(Long accountId, String toAccountNumber) {
        List<TransactionLedger> ledgers = transactionLedgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        return ledgers.stream()
                .filter(l -> l.getEntryType().equals("DEBIT"))
                .noneMatch(l -> l.getTransaction().getToAccountNumber().equals(toAccountNumber));
    }

@Transactional(rollbackFor = Exception.class)
    public Transaction initiateTransaction(Long fromAccountId, String toAccountNumber, BigDecimal amount, String emotionSignal) {

        Account senderAccount = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại!"));

        if (senderAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Số dư không đủ!");
        }

        Transaction tx = new Transaction();
        tx.setFromAccount(senderAccount);
        tx.setToAccountNumber(toAccountNumber);
        tx.setAmount(amount);
        tx.setEmotionSignal(emotionSignal);
        tx.setCreatedAt(LocalDateTime.now());

        // 🚀 GỌI RULE ENGINE DYNAMIC
        boolean isNewRecipient = checkIsNewRecipient(senderAccount.getId(), toAccountNumber);
        
        // Trả về một con số nguyên (Tổng điểm rủi ro)
        int riskScore = riskEvaluationService.evaluateRisk(tx, isNewRecipient);
        
        tx.setTotalRiskScore(riskScore);

        // 🚀 ÁP DỤNG THRESHOLD CHUẨN TRONG ĐỀ CƯƠNG (0-30, 31-70, >70)
        if (riskScore <= 30) {
            System.out.println("✅ KẾT LUẬN: LOW RISK (" + riskScore + "đ)");
            tx.setRiskLevel("LOW");
            tx.setStatus("PENDING_EXECUTION");
            tx = transactionRepository.save(tx);
            return executeLedgerTransaction(tx.getId());
        } 
        else if (riskScore <= 70) {
            System.out.println("⚠️ KẾT LUẬN: MEDIUM RISK (" + riskScore + "đ) -> BẬT KHIÊN OTP");
            tx.setRiskLevel("MEDIUM");
            tx.setStatus("PENDING_OTP");
            tx = transactionRepository.save(tx);
            
            String otp = generateOTP();
            otpService.saveOtp(tx.getId(), otp);
            System.out.println("🚨 OTP (MEDIUM RISK): " + otp);
            
            // Code gửi SMS / Email giữ nguyên...
            try {
                Message.creator(new PhoneNumber(userPhoneNumber), new PhoneNumber(twilioPhoneNumber), "FinRisk OTP: " + otp + " (3 phut)").create();
            } catch (Exception e) {
                try {
                    String userEmail = tx.getFromAccount().getUser().getEmail();
                    emailService.sendOtpEmail(userEmail, otp);
                } catch (Exception ex) {
                    System.out.println("🚨 Lỗi gửi OTP. Mã là: " + otp);
                }
            }
            return tx;
        } 
        else { 
            System.out.println("⛔ KẾT LUẬN: HIGH RISK (" + riskScore + "đ) -> BẬT KHIÊN FACE-ID");
            tx.setRiskLevel("HIGH");
            tx.setStatus("PENDING_FACE_SCAN");
            return transactionRepository.save(tx);
        }
    }

    // ... (Giữ nguyên hàm executeLedgerTransaction bên dưới)
    @Transactional(rollbackFor = Exception.class)
    public Transaction executeLedgerTransaction(Long transactionId) {

        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch!"));

        if (tx.getStatus().equals("SUCCESS") || tx.getStatus().equals("FAILED")) {
            throw new RuntimeException("Giao dịch đã được xử lý!");
        }

        Account sender = tx.getFromAccount();

        // DEBIT
        sender.setBalance(sender.getBalance().subtract(tx.getAmount()));
        accountRepository.save(sender);

        TransactionLedger debit = new TransactionLedger();
        debit.setTransaction(tx);
        debit.setAccount(sender);
        debit.setEntryType("DEBIT");
        debit.setAmount(tx.getAmount());
        debit.setBalanceAfter(sender.getBalance());
        transactionLedgerRepository.save(debit);

        // CREDIT
        accountRepository.findByAccountNumber(tx.getToAccountNumber()).ifPresent(receiver -> {
            receiver.setBalance(receiver.getBalance().add(tx.getAmount()));
            accountRepository.save(receiver);

            TransactionLedger credit = new TransactionLedger();
            credit.setTransaction(tx);
            credit.setAccount(receiver);
            credit.setEntryType("CREDIT");
            credit.setAmount(tx.getAmount());
            credit.setBalanceAfter(receiver.getBalance());
            transactionLedgerRepository.save(credit);
        });

        tx.setStatus("SUCCESS");
        return transactionRepository.save(tx);
    }
}