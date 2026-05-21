
// package com.datn.finrisk.core.services;

// import com.datn.finrisk.core.entities.Account;
// import com.datn.finrisk.core.entities.RiskPolicy;
// import com.datn.finrisk.core.entities.Transaction;
// import com.datn.finrisk.core.entities.TransactionLedger;
// import com.datn.finrisk.core.entities.UserSecurity;
// import com.datn.finrisk.core.exceptions.BusinessLogicException;
// import com.datn.finrisk.core.repository.AccountRepository;
// import com.datn.finrisk.core.repository.RiskPolicyRepository;
// import com.datn.finrisk.core.repository.TransactionLedgerRepository;
// import com.datn.finrisk.core.repository.TransactionRepository;
// import com.datn.finrisk.core.repository.UserSecurityRepository;
// import com.datn.finrisk.core.strategies.RiskActionStrategy; // Dòng ma thuật đây!
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;

// import java.math.BigDecimal;
// import java.time.LocalDateTime;
// import java.util.List;
// import java.util.Map;

// //Transaction B9: đóng vai trò tonggor chỉ huy gói toan bọ quá trinh trên initiateTransaction -> Transaction B10: Auditllogserrvice
// //Transaction B9 Phase 2: bắt đau gọi hafm xử lý tiền bạcs executeTransactionCore qua trính thực hiện UPDATE  cacs ảng account va INSERT  vao transactiion ledgers
// //Transaction B10 Phase 2 : AuditLogService
// @Service
// public class TransactionService {

//     @Autowired private AccountRepository accountRepository;
//     @Autowired private TransactionRepository transactionRepository;
//     @Autowired private TransactionLedgerRepository transactionLedgerRepository;
//     @Autowired private RiskEvaluationService riskEvaluationService;
//     @Autowired private RiskPolicyRepository riskPolicyRepo;
//     @Autowired private UserSecurityRepository userSecurityRepository;

//     //   BÍ QUYẾT LÀ ĐÂY: Spring tự gom cả 3 class Strategy vào cái Map này!
//     @Autowired
//     private Map<String, RiskActionStrategy> actionStrategies; 

//     @Transactional
//     public Transaction executeAfterOtp(Transaction tx) {
//         RiskActionStrategy strategy = actionStrategies.get("passActionStrategy");
//         return strategy.execute(tx);
//     }

//     private boolean checkIsNewRecipient(Long accountId, String toAccountNumber) {
//         // 🚀 1 query thay vì load toàn bộ lịch sử
//         return !transactionLedgerRepository
//             .existsByAccountIdAndToAccountNumber(accountId, toAccountNumber);
//     }


//     @Transactional(rollbackFor = Exception.class)
//     public Transaction initiateTransaction(Long fromAccountId, String toAccountNumber, BigDecimal amount, String description, String ip, String device) {

//         Account senderAccount = accountRepository.findByIdWithUserAndSecurity(fromAccountId)
//                 .orElseThrow(() -> new BusinessLogicException("ERR_NOT_FOUND", "Tài khoản không tồn tại!"));

//         UserSecurity security = senderAccount.getUser().getUserSecurity();

//         if (security.getPinHash() == null || security.getPinHash().trim().isEmpty()) {
//             throw new BusinessLogicException("ERR_NO_PIN_SETUP", "Tài khoản chưa thiết lập mã Smart PIN. Vui lòng thiết lập để giao dịch!");
//         }

//         if (security.getLockUntil() != null && security.getLockUntil().isAfter(LocalDateTime.now())) {
//             throw new BusinessLogicException("ERR_PIN_LOCKED", "Tài khoản đang bị tạm khóa giao dịch do nhập sai PIN nhiều lần. Vui lòng thử lại sau!");
//         }

//         // Các logic validate cơ bản
//         if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
//             throw new IllegalArgumentException("Số tiền giao dịch phải lớn hơn 0 hợp lệ!");
//         }

//         if (senderAccount.getAccountNumber().equals(toAccountNumber)) {
//             throw new IllegalArgumentException("Phát hiện gian lận: Không thể tự chuyển tiền cho chính mình!");
//         }

//         if (senderAccount.getBalance().compareTo(amount) < 0) {
//             throw new BusinessLogicException("ERR_INSUFFICIENT_BALANCE", "Số dư không đủ để thực hiện giao dịch!");
//         }

//         // Tạo giao dịch mới
//         Transaction tx = new Transaction();
//         tx.setFromAccount(senderAccount);
//         tx.setToAccountNumber(toAccountNumber);
//         tx.setAmount(amount);
//         tx.setCreatedAt(LocalDateTime.now());
//         tx.setDescription(description); 
        
//         // Đổ dữ liệu IP và Device vào Transaction trước khi lưu
//         tx.setLocationIp(ip);
//         tx.setDeviceFingerprint(device);

//         boolean isNewRecipient = checkIsNewRecipient(senderAccount.getId(), toAccountNumber);
        
//         // 1. Tính tổng điểm rủi ro
//         int riskScore = riskEvaluationService.evaluateRisk(tx, isNewRecipient);
//         tx.setTotalRiskScore(riskScore);

//         // 2. Tra cứu Policy từ Database
//         RiskPolicy policy = riskPolicyRepo.findByScore(riskScore)
//                 .orElseThrow(() -> new BusinessLogicException("ERR_POLICY_NOT_FOUND", "LỖI HỆ THỐNG: Không tìm thấy Policy xử lý cho mức điểm " + riskScore));

//         System.out.println("🔎 Tra cứu Database: Điểm " + riskScore + " rơi vào Policy [" + policy.getDescription() + "]");

//         // 3. Lấy tên Chiến thuật ra và gọi lệnh chạy!
//         RiskActionStrategy strategy = actionStrategies.get(policy.getActionBeanName());
        
//         if (strategy == null) {
//             throw new BusinessLogicException("ERR_STRATEGY_NOT_FOUND", "LỖI CODE: Không tìm thấy class xử lý cho hành động " + policy.getActionBeanName());
//         }

//         return strategy.execute(tx);
//     }
    
//     @Transactional(rollbackFor = Exception.class)
//     public Transaction executeTransactionCore(Transaction tx) {
//         System.out.println("✅ XÁC THỰC THÀNH CÔNG -> ĐÓNG MỘC TRỪ TIỀN VÀO SỔ CÁI");
        
//         tx.setStatus("SUCCESS");
//         Transaction savedTx = transactionRepository.save(tx);

//         // Trừ tiền người gửi
//         Account sender = savedTx.getFromAccount();
//         sender.setBalance(sender.getBalance().subtract(savedTx.getAmount()));
//         accountRepository.save(sender);

//         TransactionLedger debit = new TransactionLedger();
//         debit.setTransaction(savedTx);
//         debit.setAccount(sender);
//         debit.setEntryType("DEBIT");
//         debit.setAmount(savedTx.getAmount());
//         debit.setBalanceAfter(sender.getBalance());
//         transactionLedgerRepository.save(debit);

//         // Cộng tiền người nhận
//         accountRepository.findByAccountNumber(savedTx.getToAccountNumber()).ifPresent(receiver -> {
//             receiver.setBalance(receiver.getBalance().add(savedTx.getAmount()));
//             accountRepository.save(receiver);

//             TransactionLedger credit = new TransactionLedger();
//             credit.setTransaction(savedTx);
//             credit.setAccount(receiver);
//             credit.setEntryType("CREDIT");
//             credit.setAmount(savedTx.getAmount());
//             credit.setBalanceAfter(receiver.getBalance());
//             transactionLedgerRepository.save(credit);
//         });

//         return savedTx;
//     }
// }


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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.datn.finrisk.core.entities.RiskScore;

import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

//Transaction B9: đóng vai trò tonggor chỉ huy gói toan bọ quá trinh trên initiateTransaction -> Transaction B10: Auditllogserrvice
//Transaction B9 Phase 2: bắt đau gọi hafm xử lý tiền bạcs executeTransactionCore qua trính thực hiện UPDATE  cacs ảng account va INSERT  vao transactiion ledgers
//Transaction B10 Phase 2 : AuditLogService
@Service
public class TransactionService {

    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionLedgerRepository transactionLedgerRepository;
    @Autowired private RiskEvaluationService riskEvaluationService;
    @Autowired private RiskPolicyRepository riskPolicyRepo;
    @Autowired private UserSecurityRepository userSecurityRepository;
    @Autowired private BehaviorLearningService behaviorLearningService;

    //   BÍ QUYẾT LÀ ĐÂY: Spring tự gom cả 3 class Strategy vào cái Map này!
    @Autowired
    private Map<String, RiskActionStrategy> actionStrategies; 

    @Autowired private RiskScoreRepository riskScoreRepository;

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

        // 1. Kiểm tra sự tồn tại của tài khoản và thông tin bảo mật
        Account senderAccount = accountRepository.findByIdWithUserAndSecurity(fromAccountId)
                .orElseThrow(() -> new BusinessLogicException("ERR_NOT_FOUND", "Tài khoản không tồn tại!"));

        UserSecurity security = senderAccount.getUser().getUserSecurity();

        // 2. Kiểm tra thiết lập PIN và trạng thái khóa
        if (security.getPinHash() == null || security.getPinHash().trim().isEmpty()) {
            throw new BusinessLogicException("ERR_NO_PIN_SETUP", "Tài khoản chưa thiết lập mã Smart PIN. Vui lòng thiết lập để giao dịch!");
        }

        if (security.getLockUntil() != null && security.getLockUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessLogicException("ERR_PIN_LOCKED", "Tài khoản đang bị tạm khóa giao dịch. Vui lòng thử lại sau!");
        }

        // 3. Các logic validate nghiệp vụ cơ bản
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền giao dịch không hợp lệ!");
        }

        if (senderAccount.getAccountNumber().equals(toAccountNumber)) {
            throw new IllegalArgumentException("Không thể tự chuyển tiền cho chính mình!");
        }

        if (senderAccount.getBalance().compareTo(amount) < 0) {
            throw new BusinessLogicException("ERR_INSUFFICIENT_BALANCE", "Số dư không đủ để thực hiện giao dịch!");
        }

        // 4. Khởi tạo đối tượng Transaction (Chưa lưu ngay)
        Transaction tx = new Transaction();
        tx.setFromAccount(senderAccount);
        tx.setToAccountNumber(toAccountNumber);
        tx.setAmount(amount);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setDescription(description); 
        tx.setLocationIp(ip);
        tx.setDeviceFingerprint(device);
        tx.setStatus("PENDING");

        // 5. Chuẩn bị bối cảnh để Rule Engine làm việc
        boolean isNewRecipient = checkIsNewRecipient(senderAccount.getId(), toAccountNumber);
        
        // 🚀 CHIẾN THUẬT "GIỎ HÀNG": Tạo danh sách rỗng để hứng các luật vi phạm
        List<RiskScore> pendingRiskLogs = new ArrayList<>();
        
        // 6. Gọi Rule Engine để tính điểm rủi ro và thu thập bằng chứng (pendingRiskLogs)
        // Lưu ý: Bro nhớ sửa signature của hàm evaluateRisk bên RiskEvaluationService để nhận tham số này
        int riskScore = riskEvaluationService.evaluateRisk(tx, isNewRecipient, pendingRiskLogs);
        tx.setTotalRiskScore(riskScore);

        // 7. LƯU TRANSACTION TRƯỚC: Để Database sinh ID cho tx
        tx = transactionRepository.save(tx);
        System.out.println("🚀 TRANSACTION ĐÃ ĐƯỢC CHỐT SỔ. ID: " + tx.getId());

        // 8. GẮN ID VÀ LƯU CHI TIẾT CÁC LUẬT VI PHẠM
        if (!pendingRiskLogs.isEmpty()) {
            for (RiskScore log : pendingRiskLogs) {
                log.setTransaction(tx); // Đã có ID tx nên không bao giờ lỗi Transient nữa
                riskScoreRepository.save(log);
            }
            System.out.println("✅ ĐÃ GHI NHẬN " + pendingRiskLogs.size() + " BẰNG CHỨNG VI PHẠM VÀO DATABASE.");
        }

        // 9. Tra cứu Chính sách rủi ro (Policy) tương ứng với điểm số
        RiskPolicy policy = riskPolicyRepo.findByScore(riskScore)
                .orElseThrow(() -> new BusinessLogicException("ERR_POLICY_NOT_FOUND", "Không tìm thấy Policy xử lý cho mức điểm " + riskScore));

        tx.setRiskLevel(policy.getRiskLevel()); // Gán nhãn LOW, MEDIUM, HIGH từ policy vào tx
        
        System.out.println("🔎 Kết luận: Điểm " + riskScore + " -> " + policy.getRiskLevel() + " [" + policy.getDescription() + "]");

        // 10. Quyết định hành động dựa trên Policy
        RiskActionStrategy strategy = actionStrategies.get(policy.getActionBeanName());
        
        if (strategy == null) {
            throw new BusinessLogicException("ERR_STRATEGY_NOT_FOUND", "Không tìm thấy class xử lý cho hành động " + policy.getActionBeanName());
        }

        // 11. Thực thi chiến thuật (Ví dụ: Trả về SUCCESS hoặc Yêu cầu quét FaceID)
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

        //  BƯỚC 5: KÍCH HOẠT VÒNG LẶP HỌC TẬP (AI FEEDBACK LOOP)

        // Check xem STK người nhận này đã từng nhận tiền chưa
        boolean isNewRecipient = checkIsNewRecipient(sender.getId(), savedTx.getToAccountNumber());
        
        // Gọi hàm chạy ngầm (Nó sẽ tách ra một luồng riêng tự chạy, không chờ)
        behaviorLearningService.learnFromTransaction(savedTx, isNewRecipient);

        return savedTx;
    }

    // =========================================================================
    // 🚀 HÀM MỚI: HOÀN TÁC GIAO DỊCH (DOUBLE-ENTRY REVERSAL)
    // =========================================================================
    @Transactional(rollbackFor = Exception.class)
    public Transaction executeReversalCore(Transaction tx) {
        System.out.println("🔄 ADMIN YÊU CẦU HOÀN TÁC -> THỰC HIỆN ĐẢO CHIỀU SỔ CÁI");

        // 1. Cập nhật lại status của giao dịch gốc thành REVERSED
        tx.setStatus("REVERSED");
        Transaction reversedTx = transactionRepository.save(tx);

        BigDecimal reversalAmount = reversedTx.getAmount();

        // 2. CỘNG LẠI TIỀN CHO NGƯỜI GỬI (Lúc đầu họ bị DEBIT, giờ ta CREDIT lại)
        Account originalSender = reversedTx.getFromAccount();
        originalSender.setBalance(originalSender.getBalance().add(reversalAmount));
        accountRepository.save(originalSender);

        TransactionLedger refundCredit = new TransactionLedger();
        refundCredit.setTransaction(reversedTx);
        refundCredit.setAccount(originalSender);
        refundCredit.setEntryType("CREDIT"); // Đảo chiều: Trừ thành Cộng
        refundCredit.setAmount(reversalAmount);
        refundCredit.setBalanceAfter(originalSender.getBalance());
        transactionLedgerRepository.save(refundCredit);

        // 3. TRỪ LẠI TIỀN CỦA NGƯỜI NHẬN (Lúc đầu họ được CREDIT, giờ ta DEBIT lại)
        accountRepository.findByAccountNumber(reversedTx.getToAccountNumber()).ifPresent(originalReceiver -> {
            
            // Lưu ý nghiệp vụ: Trong ngân hàng thực tế, nếu tài khoản người nhận bị âm do Reversal, 
            // họ vẫn cứ trừ để đòi nợ sau. Ở đây ta trừ thẳng tay.
            originalReceiver.setBalance(originalReceiver.getBalance().subtract(reversalAmount));
            accountRepository.save(originalReceiver);

            TransactionLedger clawbackDebit = new TransactionLedger();
            clawbackDebit.setTransaction(reversedTx);
            clawbackDebit.setAccount(originalReceiver);
            clawbackDebit.setEntryType("DEBIT"); // Đảo chiều: Cộng thành Trừ
            clawbackDebit.setAmount(reversalAmount);
            clawbackDebit.setBalanceAfter(originalReceiver.getBalance());
            transactionLedgerRepository.save(clawbackDebit);
        });

        return reversedTx;
    }
}