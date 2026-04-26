// package com.datn.finrisk.core.strategies;

// import com.datn.finrisk.core.entities.Transaction;
// import com.datn.finrisk.core.repository.TransactionRepository;
// import com.datn.finrisk.core.services.EmailService;
// import com.datn.finrisk.core.services.OtpService;
// import com.twilio.Twilio;
// import com.twilio.rest.api.v2010.account.Message;
// import com.twilio.type.PhoneNumber;
// import jakarta.annotation.PostConstruct;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.stereotype.Service;

// import java.util.Random;

// @Service("otpActionStrategy") // KHỚP VỚI DB BẢNG RISK_POLICIES
// public class OtpActionStrategy implements RiskActionStrategy {

//     @Autowired private TransactionRepository transactionRepository;
//     @Autowired private OtpService otpService;
//     @Autowired private EmailService emailService;

//     @Value("${twilio.account.sid}") private String twilioAccountSid;
//     @Value("${twilio.auth.token}") private String twilioAuthToken;
//     @Value("${twilio.phone.number}") private String twilioPhoneNumber;
//     @Value("${app.test.phone.number}") private String userPhoneNumber;

//     @PostConstruct
//     public void initTwilio() {
//         Twilio.init(twilioAccountSid, twilioAuthToken);
//     }

//     private String generateOTP() {
//         return String.format("%06d", new Random().nextInt(999999));
//     }

//     @Override
//     public Transaction execute(Transaction tx) {
//         System.out.println("🟡 THỰC THI CHIẾN THUẬT: OTP_ACTION (MEDIUM_1)");
        
//         tx.setRiskLevel("MEDIUM_1");
//         // Đặt trạng thái chờ Nhập PIN (Bước 1), sau đó mới tới OTP (Bước 2)
//         tx.setStatus("PENDING_PIN_OTP"); 
//         tx = transactionRepository.save(tx);

//         // Gửi SMS OTP ngay lúc này để khách hàng nhận được tin nhắn khi đang bấm PIN
//         String otp = generateOTP();
//         otpService.saveOtp(tx.getId(), otp);
//         System.out.println("🚨 MÃ OTP (MEDIUM_1) LÀ: " + otp);

//         try {
//             Message.creator(new PhoneNumber(userPhoneNumber), new PhoneNumber(twilioPhoneNumber), "FinRisk OTP: " + otp + " (Có hiệu lực 3 phút)").create();
//         } catch (Exception e) {
//             try {
//                 String userEmail = tx.getFromAccount().getUser().getEmail();
//                 emailService.sendOtpEmail(userEmail, otp);
//             } catch (Exception ex) {
//                 System.err.println("🚨 Lỗi gửi OTP qua cả SMS và Email.");
//             }
//         }
//         return tx;
//     }
// }




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
        tx.setStatus("PENDING_PIN_OTP"); // Chặn ở trạm PIN trước
        return transactionRepository.save(tx);
    }
}