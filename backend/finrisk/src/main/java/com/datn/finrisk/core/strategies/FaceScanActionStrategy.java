package com.datn.finrisk.core.strategies;

import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.strategies.RiskActionStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("faceScanActionStrategy") // KHỚP VỚI DB
public class FaceScanActionStrategy implements RiskActionStrategy {

    @Autowired private TransactionRepository transactionRepository;

    @Override
    public Transaction execute(Transaction tx) {
        System.out.println("⛔ THỰC THI CHIẾN THUẬT: FACE_SCAN_ACTION (Bật Camera)");
        tx.setRiskLevel("HIGH");
        tx.setStatus("PENDING_FACE_SCAN");
        return transactionRepository.save(tx);
    }
}