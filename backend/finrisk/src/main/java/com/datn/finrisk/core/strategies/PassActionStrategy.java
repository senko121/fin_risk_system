
package com.datn.finrisk.core.strategies;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.TransactionLedger;
import com.datn.finrisk.core.exceptions.BusinessLogicException;
import com.datn.finrisk.core.repository.AccountRepository;
import com.datn.finrisk.core.repository.TransactionLedgerRepository;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.strategies.RiskActionStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("passActionStrategy") 
public class PassActionStrategy implements RiskActionStrategy {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionLedgerRepository transactionLedgerRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Transaction execute(Transaction tx) {
        System.out.println("✅ THỰC THI CHIẾN THUẬT: PASS_ACTION (Chuyển tiền trực tiếp)");
        tx.setRiskLevel("LOW");
        tx.setStatus("SUCCESS");
 
        Transaction savedTx = transactionRepository.save(tx);

        // Re-load sender with a pessimistic write lock — fresh balance, prevents lost updates
        Account sender = accountRepository.findByIdForUpdate(savedTx.getFromAccount().getId())
            .orElseThrow(() -> new BusinessLogicException("ERR_NOT_FOUND", "Tài khoản nguồn không tồn tại!"));

        // Re-check balance at execution time (LOW-risk path executes immediately at initiation)
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

        // Lock receiver account before crediting to prevent concurrent balance corruption
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

        return savedTx;
    }
}