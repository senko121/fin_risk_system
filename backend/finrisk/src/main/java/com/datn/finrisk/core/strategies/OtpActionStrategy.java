package com.datn.finrisk.core.strategies;

import com.datn.finrisk.core.entities.Transaction;
import com.datn.finrisk.core.repository.TransactionRepository;
import com.datn.finrisk.core.services.EmailService;
import com.datn.finrisk.core.services.OtpService;
import com.datn.finrisk.core.strategies.RiskActionStrategy;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service("otpActionStrategy") // KHỚP VỚI DB
public class OtpActionStrategy implements RiskActionStrategy {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private OtpService otpService;
    @Autowired private EmailService emailService;

    @Value("${twilio.account.sid}") private String twilioAccountSid;
    @Value("${twilio.auth.token}") private String twilioAuthToken;
    @Value("${twilio.phone.number}") private String twilioPhoneNumber;
    @Value("${app.test.phone.number}") private String userPhoneNumber;

    @PostConstruct
    public void initTwilio() {
        Twilio.init(twilioAccountSid, twilioAuthToken);
    }

    private String generateOTP() {
        return String.format("%06d", new Random().nextInt(999999));
    }

    @Override
    public Transaction execute(Transaction tx) {
        System.out.println("⚠️ THỰC THI CHIẾN THUẬT: OTP_ACTION (Yêu cầu xác thực SMS)");
        tx.setRiskLevel("MEDIUM");
        tx.setStatus("PENDING_OTP");
        tx = transactionRepository.save(tx);

        String otp = generateOTP();
        otpService.saveOtp(tx.getId(), otp);
        System.out.println("🚨 MÃ OTP LÀ: " + otp);

        try {
            Message.creator(new PhoneNumber(userPhoneNumber), new PhoneNumber(twilioPhoneNumber), "FinRisk OTP: " + otp + " (3 phut)").create();
        } catch (Exception e) {
            try {
                String userEmail = tx.getFromAccount().getUser().getEmail();
                emailService.sendOtpEmail(userEmail, otp);
            } catch (Exception ex) {
                System.out.println("🚨 Lỗi gửi OTP qua cả SMS và Email.");
            }
        }
        return tx;
    }
}