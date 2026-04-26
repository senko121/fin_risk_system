package com.datn.finrisk.core.strategies;

import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.EmailService;
import com.datn.finrisk.core.services.OtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Random;

@Service("basicFaceActionStrategy") 
public class BasicFaceActionStrategy implements RiskActionStrategy {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private OtpService otpService;
    @Autowired private EmailService emailService;

    @Override
    public Transaction execute(Transaction tx) {
        System.out.println("🟠 THỰC THI CHIẾN THUẬT: BASIC_FACE_ACTION (MEDIUM_2 - Tuân thủ QĐ 2345)");
        
        tx.setRiskLevel("MEDIUM_2");
        // Trạng thái chuỗi 3 bước: PIN -> OTP -> FACE
        tx.setStatus("PENDING_PIN_OTP_FACE"); 
        tx = transactionRepository.save(tx);

        String otp = String.format("%06d", new Random().nextInt(999999));
        otpService.saveOtp(tx.getId(), otp);
        System.out.println("🚨 MÃ OTP (MEDIUM_2) LÀ: " + otp);

        try {
            // Tạm thời gọi qua EmailService để tránh lặp lại Twilio Config dài dòng
            String userEmail = tx.getFromAccount().getUser().getEmail();
            emailService.sendOtpEmail(userEmail, otp);
        } catch (Exception ex) {
            System.err.println("🚨 Lỗi gửi OTP qua Email.");
        }
        
        return tx;
    }
}