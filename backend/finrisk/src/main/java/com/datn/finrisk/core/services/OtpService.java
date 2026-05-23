 

 

package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.Transaction;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;

import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class OtpService {

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private EmailService emailService; 

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

@Async("aiTaskExecutor")
    public CompletableFuture<Void> generateAndSendOtpAsync(Transaction tx) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        
        this.saveOtp(tx.getId(), otp);
        System.out.println("🚨 MÃ OTP TẠO MỚI LÀ: " + otp);

        try {
            Message.creator(new PhoneNumber(userPhoneNumber), new PhoneNumber(twilioPhoneNumber), "FinRisk OTP: " + otp + " (Có hiệu lực 3 phút)").create();
        } catch (Exception e) {
            log.warn("[OtpService] SMS delivery failed for tx={}, falling back to email.", tx.getId());
            try {
                String userEmail = tx.getFromAccount().getUser().getEmail();
 
                if (userEmail != null && !userEmail.trim().isEmpty()) {
                    emailService.sendOtpEmail(userEmail, otp);
                } else {
                    log.error("[OtpService] No email address for OTP fallback — user unreachable for tx={}.", tx.getId());
                }
                
            } catch (Exception ex) {
                log.error("[OtpService] OTP email fallback failed for tx={}: {}", tx.getId(), ex.getMessage(), ex);
            }
        }
        
        return CompletableFuture.completedFuture(null);
    }
public void saveOtp(Long transactionId, String otp) {
        String key = "otp_tx:" + transactionId;

        try {
            redisTemplate.opsForValue().set(key, otp, Duration.ofSeconds(OTP_EXPIRE_SECONDS));

            System.out.println("🔥 REDIS SAVE KEY: " + key);
            System.out.println("🔥 REDIS SAVE OTP: " + otp);

            String stored = redisTemplate.opsForValue().get(key);
            System.out.println("🔥 REDIS READ BACK: " + stored);

            Long ttl = redisTemplate.getExpire(key);
            System.out.println("🔥 REDIS TTL: " + ttl);

        } catch (Exception e) {
            log.error("[OtpService] Redis OTP save failed for tx={}: {}", transactionId, e.getMessage(), e);
        }
    }
 
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
 
                
                System.out.println("✅ OTP MATCH (Giữ nguyên Key cho các luồng stream phía sau)");
                return true;
            }

            System.out.println("❌ OTP KHÔNG KHỚP");
            return false;

        } catch (Exception e) {
            log.error("[OtpService] Redis OTP verify failed for tx={}: {}", transactionId, e.getMessage(), e);
            return false;
        }
    }
 
    public String generateVoiceOtp(Long transactionId) {
        String otp = String.format("%06d", new Random().nextInt(999999));
 
        this.saveOtp(transactionId, otp);
        
        System.out.println("🎤 VOICE OTP TẠO MỚI LÀ: " + otp);
        return otp;
    }
}