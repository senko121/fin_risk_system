
package com.datn.finrisk.core.strategies;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.TransactionLedger;
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