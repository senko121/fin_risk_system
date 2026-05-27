 

package com.datn.finrisk.core.strategies;

import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.entities.User;  
import com.datn.finrisk.core.exceptions.BusinessLogicException;  
import com.datn.finrisk.core.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("basicFaceActionStrategy") 
public class BasicFaceActionStrategy implements RiskActionStrategy {

    @Autowired private TransactionRepository transactionRepository;

    @Override
    public Transaction execute(Transaction tx) {
        System.out.println("🟠 THỰC THI: BASIC_FACE_ACTION (MEDIUM_2: PIN -> FACE)");
 
        User user = tx.getFromAccount().getUser();
        if (!user.hasFaceEmbeddings() && !user.hasFaceEmbedding() && !user.hasLegacyFaceImage()) {
            throw new BusinessLogicException("ERR_NO_FACE_SETUP",
                "Giao dịch vượt hạn mức. Bạn chưa cài đặt FaceID, vui lòng thiết lập trước khi thực hiện!");
        }

        tx.setRiskLevel("MEDIUM_2");
        tx.setStatus("PENDING_PIN_FACE"); 
        return transactionRepository.save(tx);
    }
}