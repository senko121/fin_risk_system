 


package com.datn.finrisk.core.strategies;

import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("otpActionStrategy") 
public class OtpActionStrategy implements RiskActionStrategy {

    @Autowired private TransactionRepository transactionRepository;

    @Override
    public Transaction execute(Transaction tx) {
        System.out.println("🟡 THỰC THI: OTP_ACTION (MEDIUM_1: PIN -> OTP)");
        tx.setRiskLevel("MEDIUM_1");
        tx.setStatus("PENDING_PIN_OTP");  
        return transactionRepository.save(tx);
    }
}