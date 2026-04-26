package com.datn.finrisk.core.strategies;

import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("pinActionStrategy") // Khớp đúng với bảng risk_policies (Mức LOW)
public class PinActionStrategy implements RiskActionStrategy {

    @Autowired private TransactionRepository transactionRepository;

    @Override
    public Transaction execute(Transaction tx) {
        System.out.println("🟢 THỰC THI CHIẾN THUẬT: PIN_ACTION (Dừng lại chờ nhập PIN)");
        
        tx.setRiskLevel("LOW");
        tx.setStatus("PENDING_PIN"); // Chặn giao dịch ở đây
        
        return transactionRepository.save(tx);
    }
}