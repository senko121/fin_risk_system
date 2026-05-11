package com.datn.finrisk.core.strategies;

import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

//Transaction B8: Nhận lệnh từ policy và lưu nháp giao dịch xxuong dataabse voi trang thay PENDING cho nguoi dung nhapp ma PIN ->
//Transaction B9: Transactionservice
@Service("pinActionStrategy")  
public class PinActionStrategy implements RiskActionStrategy {

    @Autowired private TransactionRepository transactionRepository;

    @Override
    public Transaction execute(Transaction tx) {
        System.out.println("  THỰC THI CHIẾN THUẬT: PIN_ACTION (Dừng lại chờ nhập PIN)");
        
        tx.setRiskLevel("LOW");
        tx.setStatus("PENDING_PIN");  
        
        return transactionRepository.save(tx);
    }
}