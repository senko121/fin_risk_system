// package com.datn.finrisk.core.services;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.data.redis.core.StringRedisTemplate;
// import org.springframework.stereotype.Service;

// import java.time.Duration;

// @Service
// public class OtpService {

//     @Autowired
//     private StringRedisTemplate redisTemplate;

//     private static final int OTP_EXPIRE_SECONDS = 180;

//     /**
//      * Lưu OTP lên Redis với debug full
//      */
//     public void saveOtp(Long transactionId, String otp) {
//         String key = "otp_tx:" + transactionId;

//         try {
//             // 🔥 SAVE
//             redisTemplate.opsForValue().set(key, otp, Duration.ofSeconds(OTP_EXPIRE_SECONDS));

//               // 🔥 TEST KEY (QUAN TRỌNG NHẤT)
//             redisTemplate.opsForValue().set("compare_key", "from_spring");

//             System.out.println("🔥 REDIS SAVE KEY: " + key);
//             System.out.println("🔥 REDIS SAVE OTP: " + otp);

//             // 🔥 READ BACK (quan trọng nhất)
//             String stored = redisTemplate.opsForValue().get(key);
//             System.out.println("🔥 REDIS READ BACK: " + stored);

//             // 🔥 TTL CHECK
//             Long ttl = redisTemplate.getExpire(key);
//             System.out.println("🔥 REDIS TTL: " + ttl);

//         } catch (Exception e) {
//             System.err.println("❌ REDIS ERROR (SAVE): " + e.getMessage());
//         }
//     }

//     /**
//      * Verify OTP với debug
//      */
//     public boolean verifyOtp(Long transactionId, String inputOtp) {
//         String key = "otp_tx:" + transactionId;

//         try {
//             String storedOtp = redisTemplate.opsForValue().get(key);

//             System.out.println("🔍 VERIFY KEY: " + key);
//             System.out.println("🔍 STORED OTP: " + storedOtp);
//             System.out.println("🔍 INPUT OTP: " + inputOtp);

//             if (storedOtp == null) {
//                 System.out.println("❌ OTP NULL (hết hạn hoặc chưa lưu)");
//                 return false;
//             }

//             if (storedOtp.equals(inputOtp)) {
//                 redisTemplate.delete(key);
//                 System.out.println("✅ OTP MATCH → DELETE KEY");
//                 return true;
//             }

//             System.out.println("❌ OTP KHÔNG KHỚP");
//             return false;

//         } catch (Exception e) {
//             System.err.println("❌ REDIS ERROR (VERIFY): " + e.getMessage());
//             return false;
//         }
//     }
// }





package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Transaction;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
public class OtpService {

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private EmailService emailService; // 🚀 GỌI EMAIL SERVICE VÀO ĐÂY

    @Value("${twilio.account.sid}") private String twilioAccountSid;
    @Value("${twilio.auth.token}") private String twilioAuthToken;
    @Value("${twilio.phone.number}") private String twilioPhoneNumber;
    @Value("${app.test.phone.number}") private String userPhoneNumber;

    private static final int OTP_EXPIRE_SECONDS = 180;


@PostConstruct
    public void initTwilio() {
        try {
            Twilio.init(twilioAccountSid, twilioAuthToken);
        } catch (Exception e) {
            System.err.println("Lỗi khởi tạo Twilio (Nếu test không cần SMS thì bỏ qua): " + e.getMessage());
        }
    }

    // 🚀 HÀM MỚI: TỰ ĐỘNG SINH MÃ VÀ GỬI TIN NHẮN
    public void generateAndSendOtp(Transaction tx) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        
        // Gọi hàm saveOtp sẵn có của bro bên dưới
        this.saveOtp(tx.getId(), otp);
        System.out.println("🚨 MÃ OTP TẠO MỚI LÀ: " + otp);

        try {
            Message.creator(new PhoneNumber(userPhoneNumber), new PhoneNumber(twilioPhoneNumber), "FinRisk OTP: " + otp + " (Có hiệu lực 3 phút)").create();
        } catch (Exception e) {
            System.err.println("🚨 Lỗi gửi SMS Twilio, tự động chuyển sang gửi Email...");
            try {
                String userEmail = tx.getFromAccount().getUser().getEmail();
                emailService.sendOtpEmail(userEmail, otp);
            } catch (Exception ex) {
                System.err.println("🚨 Lỗi không thể gửi OTP qua Email: " + ex.getMessage());
            }
        }
    }

    /**
     * Lưu OTP lên Redis với debug full
     */
    public void saveOtp(Long transactionId, String otp) {
        String key = "otp_tx:" + transactionId;

        try {
            // 🔥 SAVE
            redisTemplate.opsForValue().set(key, otp, Duration.ofSeconds(OTP_EXPIRE_SECONDS));

              // 🔥 TEST KEY (QUAN TRỌNG NHẤT)
            redisTemplate.opsForValue().set("compare_key", "from_spring");

            System.out.println("🔥 REDIS SAVE KEY: " + key);
            System.out.println("🔥 REDIS SAVE OTP: " + otp);

            // 🔥 READ BACK (quan trọng nhất)
            String stored = redisTemplate.opsForValue().get(key);
            System.out.println("🔥 REDIS READ BACK: " + stored);

            // 🔥 TTL CHECK
            Long ttl = redisTemplate.getExpire(key);
            System.out.println("🔥 REDIS TTL: " + ttl);

        } catch (Exception e) {
            System.err.println("❌ REDIS ERROR (SAVE): " + e.getMessage());
        }
    }

    /**
     * Verify OTP với debug
     */
    public boolean verifyOtp(Long transactionId, String inputOtp) {
        String key = "otp_tx:" + transactionId;

        try {
            String storedOtp = redisTemplate.opsForValue().get(key);

            System.out.println("🔍 VERIFY KEY: " + key);
            System.out.println("🔍 STORED OTP: " + storedOtp);
            System.out.println("🔍 INPUT OTP: " + inputOtp);

            if (storedOtp == null) {
                System.out.println("❌ OTP NULL (hết hạn hoặc chưa lưu)");
                return false;
            }

            if (storedOtp.equals(inputOtp)) {
                redisTemplate.delete(key);
                System.out.println("✅ OTP MATCH → DELETE KEY");
                return true;
            }

            System.out.println("❌ OTP KHÔNG KHỚP");
            return false;

        } catch (Exception e) {
            System.err.println("❌ REDIS ERROR (VERIFY): " + e.getMessage());
            return false;
        }
    }
}